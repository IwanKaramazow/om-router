(ns om-router.core
  (:require [om.next :as om :refer-macros [ui]]
            [om.dom :as dom]           
            [om-router.history :as hist]
            [clojure.string :as s])
  (:import [goog Uri]))

(defn get-url
  [] (-> js/window .-location .-href))


(defn url->path
  [url]
  (.getPath (goog.Uri. url)))

(defn url->query-params  
  [url]
  (let [query-data (.getQueryData (goog.Uri. url))]
     (clojure.walk/keywordize-keys (into {}
            (for [ k (.getKeys query-data)]
              [k (.get query-data k)])))))

(defn ensure-slash [path]
  (if (s/starts-with? path "/")
    path
    (str "/" path)))



(defn- resolve-segment [route rest]
  (letfn [(compute-index [route]
            (let [index (:index route)]
              (if (keyword? index)
                {:handler index}
                index)))
          (xf-handlers [route]
            (let [handlers (:handlers route)
                  root' (first handlers)
                  hooks (dissoc route :handlers)]
              (-> (reduce (fn [prev curr]
                            (conj prev {:handler curr})) [] handlers)
                  (update 0 #(merge % hooks)))))]
    (let [matched-route (select-keys route [:handler :handlers :onLeave :onEnter :redirect])
          matched-route (xf-handlers matched-route)]
        (if  (and (empty? rest) (contains? route :index))
          (let [index-route (compute-index route)]
            (conj matched-route index-route)) ;;[matched-route index-route]
          matched-route))))

(defn remove-slash [string]
  (if (and  (s/starts-with? string "/") (not= string "/"))
    (s/replace-first string "/" "")
    string))




(defn- compile-pattern* [pattern]
  (letfn [(compile-regex [string]
            (cond
              (s/includes? string ":") "([^/]+)"
              (= string "(") "(?:"
              (= string ")") ")?"
              (= string "**") "(.*)"
              (= string "*") "(.*?)"
              :else string))
          (escape-regex [string]
            ;;/[.*+?^${}()|[\]\\]/g
            ;;[.|\\]
            (s/replace string #"[.|\\]" "\\$&"))]
    (let [matcher #":([a-zA-Z_$][a-zA-Z0-9_$]*)|\*\*|\*|\(|\)"
          matches (re-seq matcher pattern)]
      (loop [index 0
             matches matches
             res {:params [] :regex ""  ;;:tokens []
                  }]
        (if-not (empty? matches)
          (let [{:keys [params regex]} res
                possible (first matches)
                param (second possible)
                match (first possible)
                start (s/index-of pattern match index)
                stop (+ start (count match))
                plain (subs pattern index start)
                ;; tokens (conj tokens plain)
                ;; res (assoc-in res [:tokens] tokens)
                regx (str regex (escape-regex plain))]
            (if param
              (let [res (-> res
                            (update-in [:params] #(conj % (keyword param)))
                            (assoc-in [:regex] (str regx (compile-regex match)))
                            ;; (assoc-in [:tokens] (conj tokens match))
                            )]
                (recur stop (next matches) res ))
              (let [res (-> res
                            (assoc-in [:regex] (str regx (compile-regex match)))
                            ;; (update-in [:tokens] #(conj % match))
                            )]
                (recur stop (next matches) res))))
          (let [remaining (subs pattern index (count pattern))
                res (-> res
                        (assoc-in [:regex] (str (:regex res) (escape-regex remaining)))
                        ;; (update-in  [:tokens] #(conj % remaining ))
                        )]
            res))))))

(def pattern-cache (atom {}))

(defn compile-pattern [pattern]
  (if-let [compiled (get @pattern-cache pattern)]
    compiled
    (let [compile (compile-pattern* pattern)]
      (swap! pattern-cache assoc pattern compile)
      compile)))

(defn extract-params [route-path path]
  (let [pattern (compile-pattern route-path)
        params (:params pattern)
        values (drop 1
                     (-> pattern
                         (:regex)
                         (re-pattern)
                         (re-seq path)
                         (first)))]
    (zipmap params values)))


(defn match [routes url]
  (let [path (url->path url)
        query (url->query-params url)
        route-data {:route/pathname path :route/query-params query :route/url url}
        all-routes routes]
    (loop [path path
           routes routes
           res (merge route-data {:route/params {} :route/components []})]
      (let [possible-paths  (keys routes)
            other (filter (fn [pos]
                            (re-find (re-pattern (:regex (compile-pattern pos)))  (remove-slash path))) possible-paths)
            _ (println "other" other)
            _ (println "possible-paths" possible-paths)]
        (if-let [match (first (filter
                               (fn [pos]
                                 (re-find (re-pattern (:regex (compile-pattern pos))) path))
                               possible-paths))]
          (let [rest (s/replace-first path (re-pattern (:regex (compile-pattern match))) "")
                route (get routes match)
                resolved-route (resolve-segment route rest)
                redirect (first (filter #(:redirect %) resolved-route))
                res (update-in res [:route/params] merge (extract-params match path))]
            (if redirect
              ;; todo duplicated code...
              (let [url (:redirect redirect)
                    path (url->path url)
                    query (url->query-params url)
                    route-data {:route/pathname path :route/query-params query :route/url url}]
                (recur path all-routes (merge route-data {:route/params {} :route/components []})))
              (let [res' (update-in res [:route/components] #(into [] (concat % resolved-route)))]
                (if-not (and (empty? rest) (nil? (:children route)))
                  (recur (ensure-slash rest) (:children route) res')
                  res'))))
          res)))))


(defn dispatch [handler] handler)


(def router-query
  [{:router [:route/url
            :route/pathname
            :route/query-params
            :route/components
             :route/action]}])



(defn Router [{:keys [dispatch componentDidMount]}]
  (letfn [(aggregate-query [props] ;; todo extract (i.e. code splitting) ?
            (let [components (get-in props [:router :route/components])
                  components (into []
                                   (map
                                    #(conj [(:handler %)] (dispatch (:handler %)))
                                    components))
                  sub-query (reduce #(if-let [query (om/get-query (second %2))]
                                       (if-not (empty? query)
                                         (conj % {(first %2) query})
                                         %)
                                       %) [] components)]
              (if (nil? sub-query)
                router-query
                (vec (concat router-query sub-query)))))
          (render* [components props]            
            (if (= (count components) 1)
              ((om/factory (first components)) props)
              ((om/factory (first components)) props
               (render* (rest components) props))))]
    
    (ui
     static om/IQuery
     (query [this]
            router-query)
     Object
     (initLocalState [this]
                     {:browser-history (hist/make-history this)})
     (componentWillMount [this]
                         (let [newRootQuery (aggregate-query (om/props this))]
                           (when (not= (om/get-query this) newRootQuery)
                             (om/set-query! this {:query newRootQuery}))))
     (componentDidMount [this]
                        (when componentDidMount (componentDidMount this)))
     (componentWillReceiveProps [this next-props]
                                (let [oldRootQuery (aggregate-query (om/props this))
                                      newRootQuery (aggregate-query next-props)]
                                  (let [handlers (get-in next-props [:router :route/components] )])
                                  (when (not= oldRootQuery newRootQuery)
                                    (om/set-query! this {:query newRootQuery}))))
     (componentWillUpdate [this next-props next-state]
                          (let [oldRootQuery (aggregate-query (om/props this))
                                newRootQuery (aggregate-query  next-props)]
                            (when (not= oldRootQuery newRootQuery)
                              (if (= newRootQuery (om/get-query this))
                                (om/set-query! this {:query router-query})
                                (om/set-query! this {:query newRootQuery})))))
     (componentDidUpdate [this prev-props prev-state]
                         (let [{{:keys [:route/pathname :route/action]} :router} (om/props this)
                               browser (str js/window.location.pathname js/window.location.search)
                               history (:browser-history (om/get-state this))]
                           (when (not= pathname (.getToken history))
                             (case action
                               :push (hist/push! history pathname)
                               :replace (hist/replace! history pathname)))))
     (render [this]
             (let [props (om/props this)
                   handlers (get-in props [:router :route/components])
                   components (into [] (map #(dispatch (:handler %)) handlers))]
               (when-not (empty? components) 
                 (render* components props)))))))

(defn get-push-query [href]
  `[(router/transact {:action :push :url ~href}) :router])

(defn get-replace-query [href]
  `[(router/transact {:action :replace :url ~href}) :router])

(defn push! [c href]
  (om/transact! c (get-push-query href)))


(defn replace! [c href]
  (om/transact! c (get-replace-query href)))




(defn link [component {:keys [className style href]} name]
  (dom/a #js {:className className
              :style style
              :href href
              :onClick #(do
                          (.preventDefault %)
                          (push! component href))} name))

(defn- run-hooks [state prev curr]
  (letfn [(replace [state url]
            (let [path (url->path url)
                  query-params (url->query-params url)]
              (-> state
                  (assoc-in [:router] (merge (match (:routes state) url)
                                            {:route/action :push})))))
          (compute-hooks [prev curr]
            (reduce (fn [hooks c]
                      (when (not (some #{c} prev))
                        (conj hooks c))) [] curr))]
    (let [xf (comp
              (map #(:onEnter %))
              (filter #(not (nil? %))))
          enter-hooks (transduce xf conj [] (compute-hooks prev curr))]
      (if-not (empty? enter-hooks)
       (reduce (fn [state hook]
                 (hook state replace)) state enter-hooks)
       state))))



;; parser
(defn read
  [{:keys [state query]} key _]
  (let [st @state]
    {:value (select-keys (get st key) query)}))

(defn mutate
  [{:keys [state reconciler component target] :as env} key {:keys [url action]}]
  {:action (fn []
             (let [routes (match (:routes @state) url)
                   prev (get-in @state [:router :route/components])]
               (swap! state #(-> %
                                 (assoc-in [:router] (merge
                                                     routes
                                                     {:route/action action}))
                                 (run-hooks prev (:route/components routes))))))})

