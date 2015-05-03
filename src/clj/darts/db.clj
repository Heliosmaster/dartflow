(ns darts.db
  (:require [clojure.java.jdbc :refer :all]
            [cheshire.core :refer [generate-string parse-string]]))

(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "database.db"})

(defn create-db []
  (try (db-do-commands db
                       (create-table-ddl :players
                                         [:name :text])
                       (create-table-ddl :matches
                                         [:name0 :text]
                                         [:rounds0 :text]
                                         [:name1 :text]
                                         [:rounds1 :text]
                                         [:starting :int]))
       (catch Exception e (println e))))

;;;;; players

(defn add-player [name]
  (insert! db :players {:name name}))

(defn list-players []
  (query db "select * from players"))

;;;;; matches

(defn normalize-match [match]
  (let [player0 (get match :p0)
        player1 (get match :p1)]
    {:name0 (:name player0)
     :rounds0 (generate-string (:rounds player0))
     :name1 (:name player1)
     :rounds1 (generate-string (:rounds player1))
     :starting (:starting-score match)}))

(defn denormalize-match [m]
  {:p0 {:name (:name0 m)
        :rounds (parse-string (:rounds0 m))}
   :p1 {:name (:name1 m)
        :rounds (parse-string (:rounds1 m))}
   :starting-score (:starting m)})

(defn add-match [match]
  (insert! db :matches (normalize-match match)))

(defn list-matches []
  (map denormalize-match
       (query db "select * from matches")))

(defn list-player-matches [name]
  (map denormalize-match
       (query db ["select * from matches where p0 = ? OR p1 = ?" name name])))
