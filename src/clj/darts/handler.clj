(ns darts.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [cheshire.core :as json]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [selmer.parser :refer [render-file]]
            [darts.db :as db]
            [prone.middleware :refer [wrap-exceptions]]
            [environ.core :refer [env]]))

(defn redirect [path]
  {:status 303
   :headers {"Location" path}})

(defroutes routes
  (GET "/" [] (render-file "templates/index.html" {:dev (env :dev?)}))
  (GET "/list-players" [] (json/generate-string (db/list-players)))
  (GET "/list-matches" [] (json/generate-string (db/list-matches)))
  (POST "/save-game" {params :params} (db/add-match (:game params)))
  (POST "/add-player/:name" [name] (do (db/add-player name)
                                       (redirect "/")))


  (resources "/")
  (not-found "Not Found"))


(def app
  (let [handler (-> routes
                    (wrap-defaults (assoc site-defaults :security {:anti-forgery false})))]
    (wrap-json-params (if (env :dev?)
                        (wrap-exceptions handler)
                        handler))))
