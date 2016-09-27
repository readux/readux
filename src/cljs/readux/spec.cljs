(ns readux.spec
  (:require [cljs.spec :as s]
            [readux.utils :refer [spec-valid?]]
            [cljs.spec :as spec]))

;; Define specs for action - cannot restrict additional keys
(s/def :readux.action/type keyword?)
(s/def :readux.action/payload any?)
(s/def :readux.action/error any?)
(s/def :readux.action/meta map?)

(s/def :readux/action
  (s/keys :req-un [:readux.action/type]
          :opt-un [:readux.action/payload
                   :readux.action/error
                   :readux.action/meta]))

;; - function serving as an action handler
(s/def :readux/action-handler
  (s/fspec :model any? :action :readux/action))

;; - an action map -- a map of action types -> reducer functions
(s/def :readux/fn-map
  (s/map-of keyword? fn?))
(s/def :readux/query-map :readux/fn-map)

(s/def :readux/action-map
  (s/map-of :readux.action/type fn?))

(s/def :readux.rmap/init any?)
(s/def :readux.rmap/actions :readux/action-map)
(s/def :readux/ctx keyword?)

;; An entry describing the data required to initialize a reducer
(s/def :readux.rmap/reducer-entry
  (s/keys :req-un [:readux.rmap/init
                   :readux.rmap/actions]
          :opt-un [:readux/ctx]))

(s/def :readux/reducer-map
  (s/map-of :readux.action/type
            (s/or ;; terminals
              :fn fn?                                   ; (comp?) reducer
              ;; non-terminals
              :action-map :readux/action-map            ; -> comp. reducer
              :reducer-entry :readux.rmap/reducer-entry ; -> reducer
              :reducer-map :readux/reducer-map)))       ; -> recursive def


(defn is-type?
  [t x]
  (= (type x) t))

(defn- is-ratom? [x]
  (is-type? reagent.ratom/RAtom x))

(defn- is-reaction? [x]
  (is-type? reagent.ratom/Reaction x))

(defn- is-atom? [x]
  (is-type? cljs.core/Atom x))

(s/def :readux/ratom is-ratom?)
(s/def :readux/atom is-atom?)
(s/def :readux/atom-like (s/or :atom is-atom?
                               :ratom is-ratom?))
(s/def :readux/reaction is-reaction?)

(s/def :readux.store/model :readux/ratom)
(s/def :readux.store/model-ro :readux/reaction)
(s/def :readux.store/reducer fn?)
(s/def :readux.store/queries :readux/atom-like)
(s/def :readux.store/dispatch fn?)
(s/def :readux/store
  (s/keys :req-un [:readux.store/model
                   :readux.store/model-ro
                   :readux.store/reducer
                   :readux.store/queries
                   :readux.store/dispatch]))

;; ----- Wrapped checks
(defn kw?
  [v]
  (spec-valid? keyword? v "Expected a keyword value"))

(defn kw-or-str?
  [v]
  (spec-valid?
    (s/or :kw keyword? :str string?) v
    "Must be string/keyword"))

(defn action?
  [v]
  (spec-valid?
    :readux/action v
    "Actions must be FSA-compliant (https://github.com/acdlite/flux-standard-action)"))

(defn action-map?
  [v]
  (spec-valid? :readux/action-map v "expected an action map"))

(defn query-map?
  [v]
  (spec-valid? :readux/query-map v "expected a query map"))

(defn nil-or-action-maps?
  [v]
  (spec/valid?
    (s/or :nil nil? :action-maps (s/coll-of :readux/action-map))
    v "Must be nil or a collection of action maps"))

(defn nil-val?
  [v]
  (spec-valid? int? v "Expected nil"))

(defn fun?
  [v]
  (spec-valid? fn? v "Expected a function"))

(defn store?
  [v]
  (spec-valid? :readux/store v "store value required."))

(defn store*?
  [v]
  (if (spec-valid? :readux/atom-like v "store ref should be a (r)atom.")
    (spec-valid? :readux/store @v "ref should point to a store map")
    false))