(ns darts.core
  (:require [ajax.core :refer [GET POST PUT] :as ajax-core]
            [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [clojure.walk :as walk]
            [cognitect.transit :as t]
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

(defonce players (atom nil))

(defn add-new-player [name]
  (swap! db #(update-in % [:players] conj {:name name})))

(defn parse-json [json]
  (walk/keywordize-keys (t/read (t/reader :json) @players)))

(defn list-players []
  (let [_ (GET "/list-players" {:handler (fn [res]
                                           (reset! players res))}
               )]
    [:ul
     (map (fn [player] ^{:key player} [:li (:name player)]) (parse-json @players))]))

(defn home-page []
  [:div [:h2 "Dartflow"]
   (list-players)
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
       [:h3 "New player"]
       [:input {:type :text
                :value @val
                :on-change (fn [e]
                             (reset! val (-> e
                                             .-target
                                             .-value)))
                :placeholder "New player name"}]
       [:button {:on-click #(POST (str "/add-player/" @val))} "Save"]])))

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
