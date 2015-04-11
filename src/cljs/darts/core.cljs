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

(def db (atom {:players #{{:name "Steven"}
                          {:name "Davide"}}
               :matches []}))

(defonce players (atom nil))
(def selected-players (atom #{}))

(defn add-new-player [name]
  (swap! db #(update-in % [:players] conj {:name name})))

(defn parse-json [json]
  (walk/keywordize-keys (t/read (t/reader :json) @players)))

(defn load-players []
  (GET "/list-players" {:handler (fn [res]
                                   (reset! players res))}))

(defn list-players []
  (load-players)
  [:ul
   (map (fn [player] ^{:key player} [:li (:name player)]) (parse-json @players))])

(defn home-page []
  [:div [:h2 "Dartflow"]
   [:ul
    [:li [:a {:href "#/new-game"} "New match"]]
    [:li [:a {:href "#/standings"} "Standings"]]]])

(defn new-game-page []
  (load-players)
  [:div
   [:a {:href "#"} "Home"]
   [:div [:h2 "New game"]
    [:a {:href "#/new-player"} "New player"]
    [:h3 "Select two players"]
    [:ul
     (map (fn [{:keys [name]}]
            ^{:key name}
            [:li [:input {:type "checkbox"
                          :disabled (and (= (count @selected-players)
                                            2)
                                         (not (@selected-players name)))

                          :on-click (fn []
                                      (if (@selected-players name)
                                        (swap! selected-players difference #{name})
                                        (swap! selected-players conj name)))}
                  name]])
          (parse-json @players))]]])

(defn redirect-to [path]
  (set! js/window.location.href path))

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
