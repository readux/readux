(ns readux.spec
  (:require [cljs.spec :as s]
            [readux.utils :refer [spec-valid?]]
            [cljs.spec :as spec]))

(s/def ::action.type keyword?)

;; Define specs for action - cannot restrict additional keys
(s/def ::action.type keyword?)
(s/def ::action.payload any?)
(s/def ::action.error any?)
(s/def ::action.meta map?)

(s/def ::action
  (s/keys :req-un [::action.type]
          :opt-un [::action.payload
                   ::action.error
                   ::action.meta]))

;; - function serving as an action handler
(s/def ::action-handler
  (s/fspec :model any? :action ::action))

;; - an action map -- a map of action types -> reducer functions
(s/def ::fn-map
  (s/map-of keyword? fn?))
(s/def ::query-map ::fn-map)

(s/def ::action-map
  (s/map-of ::action.type fn?))

(s/def ::rmap.init any?)
(s/def ::rmap.actions ::action-map)
(s/def ::ctx keyword?)

;; An entry describing the data required to initialize a reducer
(s/def ::rmap.reducer-entry
  (s/keys :req-un [::rmap.init
                   ::rmap.actions]
          :opt-un [::ctx]))

(s/def :readux/reducer-map
  (s/map-of ::action.type
            (s/or ;; terminals
              :fn fn?                             ; (comp?) reducer
              ;; non-terminals
              :action-map ::action-map            ; -> comp. reducer
              :reducer-entry ::rmap.reducer-entry ; -> reducer
              :reducer-map :readux/reducer-map))) ; -> recursive def


(defn is-type?
  [t x]
  (= (type x) t))

(defn- is-ratom? [x]
  (is-type? reagent.ratom/RAtom x))

(defn- is-reaction? [x]
  (is-type? reagent.ratom/Reaction x))

(defn- is-atom? [x]
  (is-type? cljs.core/Atom x))

(s/def ::ratom is-ratom?)
(s/def ::atom is-atom?)
(s/def ::atom-like (s/or :atom is-atom?
                               :ratom is-ratom?))
(s/def ::reaction is-reaction?)

(s/def ::store.model ::ratom)
(s/def ::store.model-ro ::reaction)
(s/def ::store.reducer fn?)
(s/def ::store.queries ::atom-like)
(s/def ::store.dispatch fn?)
(s/def ::store
  (s/keys :req-un [::store.model
                   ::store.model-ro
                   ::store.reducer
                   ::store.queries
                   ::store.dispatch]))

(defn- wrap-nilable
  [spec nilable]
  (if nilable (s/nilable spec) spec))

;; ----- Wrapped checks
(defn kw?
  ([v] (kw? v {}))
  ([v {:keys [label nil?]}]
   (spec-valid?
     (wrap-nilable keyword? nil?)
     v
     (if label (str "expected '" label "' to be a keyword")
               "expected a keyword value"))))

(defn kw-or-str?
  [v]
  (spec-valid?
    (s/or :kw keyword? :str string?) v
    "Must be string/keyword"))

(defn kw-coll?
  ([v] (kw-coll? v {}))
  ([v {:keys [label nil?]}]
   (spec-valid?
     (wrap-nilable (s/coll-of keyword?) nil?)
     v
     (if label (str "expected '" label "' to be a collection of keywords")
               "expected a collection of keywords"))))

(defn action?
  [v]
  (spec-valid?
    ::action v
    "Actions must be FSA-compliant (https://github.com/acdlite/flux-standard-action)"))

(defn action-map?
  [v]
  (spec-valid? ::action-map v "expected an action map"))

(defn query-map?
  [v]
  (spec-valid? ::query-map v "expected a query map"))

(defn nil-or-action-maps?
  [v]
  (spec/valid?
    (s/or :nil nil? :action-maps (s/coll-of ::action-map))
    v "Must be nil or a collection of action maps"))

(defn nil-val?
  [v]
  (spec-valid? int? v "Expected nil"))

(defn fun?
  [v]
  (spec-valid? fn? v "Expected a function"))

(defn store?
  [v]
  (spec-valid? ::store v "store value required."))

(defn store*?
  [v]
  (if (spec-valid? ::atom-like v "store ref should be a (r)atom.")
      (spec-valid? ::store @v "ref should point to a store map")
      false))