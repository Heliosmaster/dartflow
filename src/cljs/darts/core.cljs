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
    [:li [:a {:href "#/select-players"} "New match"]]
    [:li [:a {:href "#/standings"} "Standings"]]]])

(def game (atom {}))

(defn select-game-type-page []
  [:div
   (println @game)
   "HELLO"])

(defn select-players-page []
  (load-players)
  [:div
   [:a {:href "#"} "Home"]
   [:div [:h2 "New game"]
    [:ul
     [:li "Player 1: "
      [:span {:on-click #(reset! player1 "")} @player1]]
     [:li "Player 2: "
      [:span {:on-click #(reset! player2 "")} @player2]]]

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
                                 [:li [:button {:on-click (fn []
                                                            (reset! player name))}
                                       name]]))
                             (parse-json @players)))]
                      )]
      (if (= "" @player1)
        (select-fn player1)
        (select-fn player2)))

    [:a {:href "#/new-player"} "New player"]]])



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
                             (redirect-to "#/select-players"))} "Save"]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/select-players" []
  (session/put! :current-page #'select-players-page))

(secretary/defroute "/select-game-type" []
  (session/put! :current-page #'select-game-type-page))

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
