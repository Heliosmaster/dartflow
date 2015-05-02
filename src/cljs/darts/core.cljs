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
(def game-score (atom 310))

(defn both-players-selected? []
  (and (not= @player1 "")
       (not= @player2 "")))

(def example-game-2 (atom {0 {:name "Davide"
                              :rounds []}
                           1 {:name "Steven"
                              :rounds []}
                           :starting-score 310}))

(def example-game (atom {0 {:name "Davide"
                            :rounds [34 50 23 14 0]}
                         1 {:name "Steven"
                            :rounds [23 68 7 13]}
                         :starting-score 310}))

(defn remaining-points [{:keys [rounds]} starting-score]
  (- starting-score (reduce + 0 rounds)))

(def message (atom ""))

(def game example-game)

(def score (atom ""))
(def current-player (atom 0))

(defn valid-score? [score points]
  (and (<= score 180)
       (<= score points)))

(defn points [player]
  (remaining-points (get @game player) (:starting-score @game)))

(defn record-score [player new-score]
  (swap! game #(update-in %1 [%2 :rounds] conj (js/parseInt %3))
         player
         new-score)
  (reset! score "")
  (swap! current-player #(mod (inc %) 2)))

(defn clear-message []
  (reset! message ""))

(defn submit-score []
  (let [new-score (js/parseInt @score)
        points (points @current-player)]
    (if (valid-score? new-score points)
      (record-score @current-player @score)
      (reset! message "Score not valid"))))

(defn numpad []
  [:div
   (map (fn [n]
          ^{:key n}[:button
                    {:on-click (fn []
                                 (swap! score str n)
                                 (clear-message))}
                    n])
        (range 10))
   [:button {:on-click (fn []
                         (swap! score #(subs % 0 (dec (count %)))))} "←"]
   [:button {:on-click (fn []
                         (reset! score "")
                         (clear-message))} "Clear"]
   [:button {:on-click submit-score} "Enter"]])




(defn play-page []
  [:div
   @message
   [:div
    [:span "Player 1: " (remaining-points (get @game 0) (:starting-score @game))]
    [:br]
    [:span "Player 2: " (remaining-points (get @game 1) (:starting-score @game))]]
   [:div
    [:br ]
    (str "Player " (+ 1 @current-player) " turn")
    [:br]
    [:input {:type :text
             :value @score
             :on-key-press (fn [e]
                             (when (= (.-key e) "Enter")
                               (submit-score)))
             :on-change (fn [e] (let [value (str (-> e .-target .-value))
                                      value (apply str (filter #(contains? #{\1 \2 \3 \4 \5 \6 \7 \8 \9 \0} %)
                                                               value))]
                                  (reset! score value)))}]
    [:br]
    [numpad]
    (println @game)]])


(defn select-game-pane []
  [:div
   [:div "Starting score: "
    [:select {:id "score"
              :on-change #(reset! game-score (.-value (js/document.getElementById "score")))}
     (map (fn [score] ^{:key score} [:option score]) [310 410 510])]]])

(defn new-game-page []
  (load-players)
  [:div
   [:a {:href "#"} "Home"]
   [:div [:h2 "New game"]
    [:a {:href "#/new-player"} "New player"]
    [:ul
     [:li (str "Player 1: ")
      [:select {:id "player1"
                :value @player1
                :on-change #(reset! player1 (.-value (js/document.getElementById "player1")))}

       (doall
        (concat [^{:key "1-nil"}[:option ""]]
                (map (fn [{:keys [name]}]
                       ^{:key name}[:option name])
                     (parse-json @players))))]]
     [:li (str "Player 2: ")
      [:select {:id "player2"
                :value @player2
                :on-change #(reset! player2 (.-value (js/document.getElementById "player2")))}
       (doall
        (concat [^{:key "2-nil"}[:option ""]]
                (map (fn [{:keys [name]}]
                       (when (not= @player1 name)
                         ^{:key name}[:option name]))
                     (parse-json @players))))]]]
    [select-game-pane]]
   (when (both-players-selected?)
     [:div
      [:button {:on-click (fn []
                            (reset! player1 "")
                            (reset! game-score 310)
                            (reset! player2 ""))} "Reset"]
      [:button {:on-click (fn []
                            (swap! game #(assoc %1
                                                0 {:name %2
                                                   :rounds []}
                                                1 {:name %3
                                                   :rounds []}
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

(defn home-page []
  [:div [:h2 "Dartflow"]
   [:ul
    [:li [:a {:href "#/new-game"} "New match"]]
    [:li [:a {:href "#/standings"} "Standings"]]]])

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
