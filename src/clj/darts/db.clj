(ns darts.db
  (:require [clojure.java.jdbc :refer :all]))

(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "database.db"})

(defn create-db []
  (try (db-do-commands db
                       (create-table-ddl :players
                                         [:name :text]))
       (catch Exception e (println e))))

(defn add-player [name]
  (insert! db :players {:name name}))

(defn list-players []
  (query db "select * from players"))
