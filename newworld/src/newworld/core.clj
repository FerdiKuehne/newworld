(ns newworld.core
  (:import datomic.Util java.util.Random)
  (:require [datomic.api :as d]
            [clojure.java.io :as io]))

(def resource io/resource)

;;uri for db

(def uri-db "datomic:mem://")
;;start db
(defn scratch-conn []
  (let [uri (str uri-db (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))
;;simple connection
(def conn (scratch-conn))

(defn read-all
  "Read all forms in f, where f is any resource that can
   be opened by io/reader"
  [f]
  (Util/readAll (io/reader f)))

(defn transact-all
  "Load and run all transactions from f, where f is any
   resource that can be opened by io/reader."
  [conn f]
  (loop [n 0
         [tx & more] (read-all f)]
    (if tx
      (recur (+ n (count (:tx-data  @(d/transact conn tx))))
             more)
      {:datoms n})))

(transact-all conn (resource "file.edn"))

(transact-all conn (resource "ursprung.edn"))

(transact-all conn (resource "user.edn"))

(def db (d/db conn))

(def q-result
  (d/q '[:find [?e ...]
         :where [?e :book/title]]
       db))

(def ent (d/entity db (first q-result)))

(def id (d/tempid :db.part/user))

(def querry-result (d/q '[:find ?e . :where [?e :db/doc "myuser"]] db))

(def ent (d/entity db querry-result))

(def mynewbook [{:db/id id
                 :book/title "Steppenwolf"
                 :book/author "Herman Hesse"
                 :book/publishtime "1928"
                 :book/gattung "Roman"}])

(def db-1 (-> @(d/transact conn mynewbook) :db-after))

(def q-search (d/q '[:find [?e ...]
                   :where [?e :book/title]]
                 db-1))

(map (fn [e] (d/touch (d/entity db-1 e))) q-search)

(spit "lib.txt" (vec (map (fn [e] (d/touch (d/entity db-1 e))) q-search)))

(spit "lib.txt" (apply str (map (fn [e] (str {:title (:book/title e)
                                          :author (:book/author e)
                                          :time  (:book/publishtime e)
                                          :gattung (:book/gattung e)} "\n")) (map (fn [e] (d/touch (d/entity db-1 e))) q-search))))


(slurp "lib.txt")

(spit "lib.txt" "123 \n 123 \n123")

(def listofauthor
  (d/q '[:find [?e ...]
         :where [_ :book/author ?e]] db-1))

listofauthor

(def listofgattung
  (d/q '[:find [?e ...]
         :where [_ :book/gattung ?e]] db-1))
listofgattung

(def listoftime
  (d/q '[:find [?e ...]
         :where [_ :book/publishtime ?e]] db-1))

listoftime

(def listoftitles
  (d/q '[:find [?e ...]
         :where [_ :book/title ?e]] db-1))

listoftitles
;; collection of lists
(def upawupa
  (d/q '[:find [?t ?a]
         :where [?book :book/title ?t]
                [?book :book/author ?a]] db-1))

upawupa


(def letsfetz
  (d/q '[:find [(pull ?e [:book/title :book/author]) ...]
         :where [?e :book/title]] db-1))

letsfetz

(def count
  (d/q '[:find (count ?e) .
         :where [?e :book/author]] db-1))

count

(def username [:user/name "mustermann"])

(def db (:db-after @(d/transact
                     conn
                     [{:db/id (d/tempid :db.part/user)
                       :book/title "Die Betrogene"
                       :book/author "Thomas Mann"
                       :book/publishtime "1953"
                       :book/gattung "Erzählung"}
                      {:db/id (d/tempid :db.part/tx)
                       :source/user username}])))


;; database t of tx1-result
(def t (d/basis-t db))

;; transaction of tx1-result
(def tx (d/t->tx t))

;; wall clock time of tx
(def safe (:db/txInstant (d/entity db tx)))


(d/as-of db tx)


(def book [:book/title "Die Betrogene"])


(d/q '[:find ?e ?v ?email ?inst ?added
            :where
            [?e :book/title ?v ?tx ?added]
            [?tx :source/user ?user]
            [?tx :db/txInstant ?inst]
            [?user :user/name ?email]]
     db)

(d/history (d/db conn))

(defrecord science [volt current resistance frequency])

(def R20
  #{(science. "30 V" "10 A" "12k Ohm" "10Mhz")
    (science. "3.3 V" "3.5 mA" "1k Ohm" "1khz")
    (science. "10 V" "1 mA" "10k Ohm" "1Ghz")})

(defn tuplify
  "Returns a vector of the vals at keys ks in map."
  [m ks]
  (mapv #(get m %) ks))

(defn maps->rel
  "Convert a collection of maps into a relation, returning
   the tuplification of each map by ks"
  [maps ks]
  (mapv #(tuplify % ks) maps))


(def testframe
  (maps->rel R20 [:volt :current :resistance :frequency]))


(d/q '[:find [?r ...]
       :where [?v ?a ?r ?f]]
     testframe)


(d/release conn)


(def hello
  (d/function '{:lang :clojure
                :params [name]
                :code (str "Hello, " name)}))

(hello "abc")


;; bind vars  ->input
(d/q '[:find [?first ?last]
      :in ?first ?last]
     "John" "Doe")
;; bind tuples
(d/q '[:find [?first ?last]
      :in [?first ?last]]
     ["John" "Doe"])

;; bind a collection
(d/q '[:find [?first ...]
      :in [?first ...]]
    ["John" "Jane" "Phineas" "John"])
;; bind a relation
(d/q '[:find [?first ...]
      :in [[?first ?last]]]
    [["John" "Doe"]
     ["Jane" "Doe"]
     ["John" "D"]])

;; bind a "database"
(d/q '[:find [?first ...]
      :where [_ :first-name ?first]]
    [[42 :first-name "John"]
     [42 :last-name "Doe"]
     [43 :first-name "Jane"]
     [43 :last-name "Doe"]])
