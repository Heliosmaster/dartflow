(ns darts.core
  (:require [ajax.core :refer [GET POST PUT] :as ajax-core]
            [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [clojure.walk :as walk]
            [clojure.set :refer [difference]]
            [cognitect.transit :as t]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react])
  (:import goog.History))

;; -------------------------
;; Views

(defn redirect-to [path]
  (set! js/window.location.href path))

(defonce players (atom nil))

(defn parse-json [json]
  (walk/keywordize-keys (t/read (t/reader :json) @players)))

(defn load-players []
  (GET "/list-players" {:handler (fn [res]
                                   (reset! players res))}))

(defn list-players []
  (load-players)
  [:ul
   (map (fn [player] ^{:key player} [:li (:name player)]) (parse-json @players))])

(def player1 (atom ""))
(def player2 (atom ""))

(defn both-players-selected? []
  (and (not= @player1 "")
       (not= @player2 "")))

(defn home-page []
  [:div [:h2 "Dartflow"]
   [:ul
    [:li [:a {:href "#/new-game"} "New match"]]
    [:li [:a {:href "#/standings"} "Standings"]]]])

(def game (atom {}))

(defn play-page []
  [:div
   (println @game)
   "HELLO"])

(defn select-player-pane []
  [:div
   (when (both-players-selected?)
     [:div
      [:button {:on-click (fn []
                            (reset! player1 "")
                            (reset! player2 ""))} "Reset"]
      [:button {:on-click (fn []
                            (swap! game #(assoc %1
                                                :player1 %2
                                                :player2 %3) @player1 @player2)
                            (redirect-to "#/select-game-type"))} "Confirm"]])
   (let [select-fn (fn [player]
                     [:ul
                      (doall
                       (map (fn [{:keys [name]}]
                              (when (and (not= name @player1)
                                         (not= name @player2))
                                ^{:key name}
                                [:li
                                 [:button {:on-click (fn []
                                                       (reset! player name))}
                                  name]]))
                            (parse-json @players)))]
                     )]
     (if (= "" @player1)
       (select-fn player1)
       (select-fn player2)))
   [:a {:href "#/new-player"} "New player"]])

(def game-score (atom 310))

(defn select-game-pane []
  [:div
   [:p "Starting score: " @game-score]
   (map (fn [score] ^{:key score} [:button {:on-click #(reset! game-score score)} score]) [310 410 510])])

(defn new-game-page []
  (load-players)
  [:div
   [:a {:href "#"} "Home"]
   [:div [:h2 "New game"]
    [:ul
     [:li (str "Player 1: " @player1)]
     [:li (str "Player 2: " @player2)]]
    [select-player-pane]
    [select-game-pane]]
   (when (both-players-selected?)
     [:div
      [:button {:on-click (fn []
                            (reset! player1 "")
                            (reset! game-score 310)
                            (reset! player2 ""))} "Reset"]
      [:button {:on-click (fn []
                            (swap! game #(assoc %1
                                                :player1 %2
                                                :player2 %3
                                                :starting-score %4) @player1 @player2 @game-score)
                            (redirect-to "#/play"))} "Confirm"]])])



(defn new-player-page []
  (let [val (atom "")]
    (fn []
      [:div
       [:a {:href "#"} "Home"]
       [:h3 "New player"]
       [:input {:type :text
                :value @val
                :on-change (fn [e]
                             (reset! val (-> e
                                             .-target
                                             .-value)))
                :placeholder "New player name"}]
       [:button {:on-click (fn []
                             (POST (str "/add-player/" @val))
                             (redirect-to "#/new-game"))} "Save"]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/new-game" []
  (session/put! :current-page #'new-game-page))

(secretary/defroute "/play" []
  (session/put! :current-page #'play-page))

(secretary/defroute "/new-player" []
  (session/put! :current-page #'new-player-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
