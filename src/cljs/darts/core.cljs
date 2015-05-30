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

(def players (atom nil))

(defn load-players []
  (GET "/list-players"
       {:handler (fn [res]
                   (->> res
                        (t/read (t/reader :json))
                        (walk/keywordize-keys)
                        (reset! players)))}))

(def player1 (atom ""))
(def player2 (atom ""))
(def game-score (atom 310))

(defn other-player-id [id]
  (if (= :p0 id) :p1 :p0))

(defn both-players-selected? []
  (and (not= @player1 "")
       (not= @player2 "")))

(def example-game-2 (atom {:p0 {:name "Davide"
                                :rounds []}
                           :p1 {:name "Steven"
                                :rounds []}
                           :starting-score 310}))

(def finished-game (atom {:p0 {:name "Davide"
                               :rounds [180]}
                          :p1 {:name "Steven"
                               :rounds [0]}
                          :starting-score 180}))

(def example-game (atom {:p0 {:name "Davide"
                              :rounds [34 50 23 14 0]}
                         :p1 {:name "Steven"
                              :rounds [23 68 7 13]}
                         :starting-score 310}))

(def message (atom ""))

(def game finished-game)

(def score (atom ""))
(def current-player (atom :p0))

(defn error-message [score points]
  (cond
    (> score 180) "Score must be <= 180"
    (> score points) "You cannot score more points than remaining ones. Over?"))

(defn points [player-id]
  (- (:starting-score @game)
     (reduce + 0 (:rounds (get @game player-id)))) )

(defn record-score [player new-score]
  (swap! game #(update-in %1 [%2 :rounds] conj (js/parseInt %3))
         player
         new-score)
  (reset! score "")
  (swap! current-player other-player-id))

(defn clear-message []
  (reset! message ""))

(defn submit-score []
  (let [new-score (js/parseInt @score)
        points (points @current-player)]
    (if-let [error (error-message new-score points)]
      (reset! message error)
      (record-score @current-player @score))))

(defn average [player-id]
  (let [rounds (:rounds (get @game player-id))]
    (if (> (count rounds)
           0)
      (-> (reduce + rounds)
          (/ (count rounds))
          (* 100)
          Math/round
          (/ 100))
      0)))

(defn print-rounds [player-id]
  (str (:rounds (get @game player-id))))

(defn numpad []
  [:div
   (map (fn [n]
          ^{:key n}
          [:button
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

(defn player-name [player-id]
  (:name (get @game player-id)))

(defn print-player [player-id]
  [:span  (player-name player-id) ": " (points player-id) " " (print-rounds player-id) " avg: " (average player-id)])

(defn game-in-progress? []
  (and (> (points :p0) 0)
       (> (points :p1) 0)))

(defn result-pane []
  (let [winning-player-id (first (filter (fn [player]
                                           (= 0 (points player)))
                                         [:p0 :p1]))
        losing-player-id (other-player-id winning-player-id)]
    [:div
     [:p "Congratulations! " (player-name winning-player-id)]
     [:p
      "You won in " (count (:rounds (get @game winning-player-id))) " rounds, "
      "with an average of " (average winning-player-id) " (TODO ± from last time)"]
     [:p (player-name losing-player-id) " lost with an average of " (average losing-player-id) " (TODO ± from last time)"]
     [:button {:on-click (fn []
                           (POST "/save-game" {:params {:game @game}
                                               :format :json}))}
      "Save"]]))

(defn play-page []
  (if (game-in-progress?)
    [:div
     @message
     [:div
      (print-player :p0)
      [:br]
      (print-player :p1)]
     [:div
      [:br]
      (str (player-name @current-player) " to shoot")
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
      [:br]
      [:a {:href "/"} "Quit, let's have a pint!"]
      (println @game)]]
    result-pane)
  )

(defn select-game-pane []
  [:div
   [:div "Starting score: "
    [:select {:id "score"
              :on-change #(reset! game-score
                                  (.-value (js/document.getElementById "score")))}
     (map (fn [score]
            ^{:key score}
            [:option score])
          [310 410 510])]]])

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
                     @players)))]]
     [:li (str "Player 2: ")
      [:select {:id "player2"
                :value @player2
                :on-change #(reset! player2 (.-value (js/document.getElementById "player2")))}
       (doall
        (concat [^{:key "2-nil"}[:option ""]]
                (map (fn [{:keys [name]}]
                       (when (not= @player1 name)
                         ^{:key name}[:option name]))
                     @players)))]]]
    [select-game-pane]]
   (when (both-players-selected?)
     [:div
      [:button {:on-click (fn []
                            (reset! player1 "")
                            (reset! game-score 310)
                            (reset! player2 ""))} "Reset"]
      [:button {:on-click (fn []
                            (swap! game #(assoc %1
                                                :p0 {:name %2
                                                     :rounds []}
                                                :p1 {:name %3
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

(def matches (atom nil))

(defn standings-component []
  [:div
   (println @matches)
   [:div "Hello"]])

(defn winning-player [{:keys [starting-score] :as match}]
  (let [score-0 (reduce + 0 (:rounds (:p0 match)))]
    (if (= starting-score score-0)
      0
      1)))

(defn averages [match]
  (let [rounds-0 (:rounds (:p0 match))
        rounds-1 (:rounds (:p1 match))
        average-0 (/ (reduce + 0 rounds-0)
                     (count rounds-0))
        average-1 (/ (reduce + 0 rounds-1)
                     (count rounds-1))
]
    [average-0 average-1]))

(defn render-match [match]
  (str (:name (:p0 match))
       " v. "
       (:name (:p1 match))
       (if (= 0 (winning-player match))
         " 1 - 0 "
         " 0 - 1 ")
       "(avg. " (clojure.string/join ", " (averages match)) ")"))

(defn render-matches [ms]
  (when ms
    (into [:ul]
     (map-indexed (fn [i m] ^{:key i}
                    [:li (render-match m)])
          ms)))
  )

(defn standings-page []
  (let [get-stuff #(GET "/list-matches"
                        {:handler (fn [r]
                                    (->> r
                                         (t/read (t/reader :json))
                                         (walk/keywordize-keys)
                                         (reset! matches)))})]
    (get-stuff)
    (fn []
      (render-matches @matches))))

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

(secretary/defroute "/standings" []
  (session/put! :current-page #'standings-page))

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
