(ns om-router.example
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [om-router.core :as router]
            [om-router.history :as history]))


(enable-console-print!)



(defn handleEnter [state replace]
  
  (replace state "/not-logged-in"))

(defn testLeave [state reconciler]
  (println "test leave first")
  true)

(defn testLeave2 [state reconciler]
  (println "test leave second")
  true)



(def routes
  {"/" {:handlers [:app :fun]
        :index :home ;; index-route for route "/"
        :children {"/" {:handler :sub
                        
                        :children {"hello(/:name)" {:handler :hello}
                                   "about" {:handler :about
                                            :onLeave testLeave2
                                            :children {"swag" {:handler :swag
                                                               :onLeave testLeave}
                                                       
                                                      }
                                            }
                                   "contact" {:handler :contact}
                                   "test/:id/test/:swag" {:handler :test }
                                   "files/**/*.jpg" {:handler :jpg}
                                   "redirect" {:redirect "/lala"}
                                   "escape.!{}+" {:handler :escape}
                                    "*" {:handler :not-found}
                                   }}}}
   })

(def app-state {:app/title "App!"
                :home/body "This is the body of the home page queried w/ [:home/body]"
                :routes routes})







;; parser
(defmulti read om/dispatch)

;; hook up the router's read
(defmethod read :router
  [env key params]
  (router/read env key params) )

(defmethod read :navbar/items
  [{:keys [state query] } key _]
  (let [st @state]
    {:value (om/db->tree query (get-in st [key]) st)}))



(defmethod read :default
  [{:keys [parser query state] :as env} key params]
  (if (some #{key} [:app :home :test :hello])
    ;; recursively call the parser,
    ;; i.e. we ignore :app || :home || :test || :union
    ;; and walk a little deeper in the query
    {:value (parser env query)}
    {:value (get @state key)}))



(defmulti mutate om/dispatch)

(defmethod mutate 'load/it
  [{:keys [state component]} key {:keys [data]}]
  {:action #(swap! state (fn [st]
                           (merge st
                                  (om/tree->db component data true))))})

;; hoop up the router's mutation
(defmethod mutate 'router/transact
  [env key params]
   (router/mutate env key params))





;; components


;; retrieves the component given a key (think :handler or :index in your routes) 
(defmulti find-component router/dispatch)

(defui Test
  static om/IQuery
  (query [_]
         '[{:router [:route/params]}])
  Object
  (render [this]
          (let [{:keys [router]} (:test (om/props this))
                {:keys [route/params]} router]
            (dom/div nil
                     (dom/h1 nil "Test Route")
                     (dom/div nil (str "-> params" params) )))))

(defmethod find-component :test [_] Test)

(defui MenuItem
  static om/Ident
  (ident [_ {:keys [id]}]
         [:navbar/items-by-id id])
  static om/IQuery
  (query [_]
         [:id :name :path])
  Object
  (render [this]        
          (let [{:keys [name path]} (om/props this)]
            (dom/li nil
                    (router/link this {:className "nav-item" :path path} name)))))

(def menu-item (om/factory MenuItem {:keyfn #(:id %)}))

(defn navbar [items]
  (dom/ul nil
          (map menu-item items)))


(def nav {:navbar/items [{:id 0 :name "hello" :path "/hello"}
                         {:id 11 :name "hello maxim" :path "/hello/maxim"}
                         {:id 13 :name "ok" :path "/ok"}
                         {:id 1 :name "home" :path "/"}
                         ;; {:id 2 :name "union" :path "/union"}
                         {:id 3 :name "about" :path "/about"}
                         {:id 4 :name "not-found" :path "/lala"}
                         {:id 5 :name "swag" :path "/about/swag/"}
                         {:id 6 :name "param-heaven" :path "/test/123/test/456"}
                         {:id 7 :name "strange paths" :path "/files/path/to/swag.jpg"}
                         {:id 8 :name "whitespace" :path "/files/pat h/to/swag.jpg"}
                         {:id 9 :name "redirect" :path "/redirect"}
                         {:id 20 :name "escape.!" :path "/escape.!{}+"}]})

(defui App
  static om/IQuery
  (query [this]
         [{:router [:route/pathname]} :app/title {:navbar/items (om/get-query MenuItem)}])
  Object
  (componentDidMount [this]
                     (om/transact! this `[(load/it {:data ~nav})]))
  (render [this]
          (let [{:keys [app/title navbar/items]
                 {:keys [route/pathname]} :router} (:app (om/props this))]
            (dom/div nil
                     (dom/h1 nil title)
                     (dom/div nil (str "path => " pathname))
                     (navbar items)
                     (om/children this)))))


(defmethod find-component :app [_] App)

(defui Hello
  static om/IQuery
  (query [this]
         [{:router [:route/params]}])
  Object
  (render [this]
          (let [{{:keys [route/params]} :router} (:hello (om/props this))]
            (dom/div nil
                     (dom/h1 nil "Hello")
                     (dom/p nil "params:" (:name params))
                     ))))

(defmethod find-component :hello [_] Hello)

(defui Home
  static om/IQuery
  (query [_]
         [:home/body])
  Object
  (render [this]
          (let [{:keys [home/body]} (:home (om/props this))]
            (dom/div nil
                     (dom/h1 nil  "Home!")
                     (dom/p nil body)))))

(defmethod find-component :home [_] Home)

(defui About
  Object
  (render [this]
          (dom/div nil 
                   (dom/h1 nil "About")
                   (om/children this))))

(defmethod find-component :about [_] About)

(defn not-found []
  (dom/h1 nil "This is the not-found page"))

(defmethod find-component :not-found [_] not-found)

(defn swag []
  (dom/h1 nil "The swag has arrived"))

(defmethod find-component :swag [_] swag)

(defui Sub
  Object
  (render [this]
          (dom/div nil
                   (dom/h1 nil "Sub")
                   (om/children this))))

(defmethod find-component :sub [_] Sub)


(defn jpg []
  (dom/h1 nil "JPG"))

(defmethod find-component :jpg [_] jpg)

(defui Fun
  Object
  (render [this]
          (dom/div nil
                   (dom/h1 nil "fun part")
                   (om/children this))))

(defmethod find-component :fun [_] Fun)



(defn escape []
  (dom/h1 nil  "escape.*!"))

(defmethod find-component :escape [_] escape)



(defonce reconciler
  (om/reconciler
   {:state app-state
    :parser (om/parser {:read read :mutate mutate})}))


(defui Root
  static om/IQuery
  (query [_]
         '[:router])
  Object
  (render [this]
          (dom/h1 nil "test")))

(defn routerDidMount [router]
  (om/transact! router '[(launch-fireworks!)]))

(om/add-root!
 reconciler (router/Router {:dispatch find-component
                            :componentDidMount routerDidMount})
 (gdom/getElement "app"))


