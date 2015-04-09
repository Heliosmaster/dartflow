(ns darts.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react])
  (:import goog.History))

;; -------------------------
;; Views

(def db (atom {:players #{{:name "Steven"}
                          {:name "Davide"}}
               :matches []}))

(defn add-new-player [name]
  (swap! db #(update-in % [:players] conj {:name name})))

(defn list-players []
  [:ul
   (map (fn [player] ^{:key player} [:li (:name player)]) (:players @db))])

(defn home-page []
  [:div [:h2 "Dartflow"]
   [:ul
    [:li [:a {:href "#/new-game"} "New match"]]
    [:li [:a {:href "#/standings"} "Standings"]]]])

(defn new-game-page []
  [:a {:href "#"} "Home"]

  [:div [:h2 "New game"]
   [:h3 "List of players"]
   [:a {:href "#/new-player"} "New player"]
   [:ul
    (map (fn [player] ^{:key player} [:li (:name player)]) (:players @db))]])

(defn new-player-page []
  (let [val (atom "")]
    (fn []
      [:div
       [:a {:href "#"} "Home"]
       [:h2 "Existing players"]
       (list-players)
       [:h3 "New player"]
       [:input {:type :text
                :value @val
                :on-change (fn [e]
                             (reset! val (-> e
                                             .-target
                                             .-value)))
                :placeholder "New player name"}]
       [:button {:on-click #(add-new-player @val)} "Save"]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/new-game" []
  (session/put! :current-page #'new-game-page))

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
