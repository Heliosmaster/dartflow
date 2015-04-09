(ns darts.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
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
  (GET "/list-players" [] (db/list-players))
  (POST "/add-player/:name" [name]
        (do (db/add-player name)
            (redirect "/")))


  (resources "/")
  (not-found "Not Found"))


(def app
  (let [handler (wrap-defaults routes (assoc site-defaults :security {:anti-forgery false}))]
    (if (env :dev?)
      (wrap-exceptions handler)
      handler)))
