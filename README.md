# Om-Router

 
> *“Avoidance of boredom is the only worthy mode of action.
Life otherwise is not worth living.”*
> *&mdash; Nassim Nicholas Taleb*

Apparently Clojure(Script) libraries have to start with a random quote...
Since we got that out of the way, let's do business:
Om-Router is a (nested) client-side router written in ClojureScript for Om.Next.

The main goal of this project is to provide a way to swap out components based
on the url of the browser and aggregate the queries in the router.


### Thanks

Special thanks to @anmonteiro for answering a lot of my questions about Om Next on Slack. Without his superior knowledge this wouldn't have been possible.
Also, thanks to David Nolen & the Ancients for supplying us, humans, with the power
of the Graph and Om Next.


### RoadMap

* [x] initial implementation
* [ ] code splitting
* [ ] docs
* [ ] examples
* [ ] tests
* [ ] benchmarking & performance
* [ ] server side rendering (Cellophane?)


### Usage

```[om-router "0.1.0-SNAPSHOT"]```

```clojure
;; 0. Don't forget to require the router...
(ns om-router.example
  (:require [om-router.core :as router]))

;; 1. define some routes
(def routes
  {"/" {:handler :app
        :index :home ;;index-route for "/"
        :children {"about" {:handler :about}
                   "users/:id" {:handler :user
                                :onEnter authenticated?} 
                   "content" {:redirect "/about"} ;; redirect to /about
                   "*" {:handler :not-found}}}})

;; 2. add the routes to your initial app-state
(def app-state {:routes routes ;; under `:routes` important ;) 
                :something/else "rest of your app"})

;; 3. Hook up the router to your parser
(defmulti read om/dispatch)

;; hook up the router's read to `:router`
(defmethod read :router
  [env key params]
  (router/read env key params))

(defmulti mutate om/dispatch)

;; hoop up the router's mutation to 'router/transact
(defmethod mutate 'router/transact
  [env key params]
  (router/mutate env key params))

;; 4. define a multimethod to help the router find components
(defmulti find-component router/dispatch)

;; 5. Write some components
(defui App
  static om/IQuery
  (query [this]
         [{:router [:route/pathname]} ;;rest of your query..
          ])
 Object
 (render [this]
        (let [{{:keys [route/pathname]} :router} (:app (om/props this))]
          ;; everything will be available under `:app` in (om/props this)
          ;; why? see below in 6., the router will dispatch on :app to find App
          ;; it will aggregate the query found here under :app (e.g. a join)
          (dom/div nil
                   (dom/h1 nil "App")
                   (dom/div nil "pathname: " pathname)))))
;; etc. rest of your components

;; 6. This is important, the keys you defined under `:handler` in the route config
;; are necessary to help find the components.
;; When matching the routes, the router will dispatch for example on :app to find component `App` as defined below.
(defmethod find-component :app [_] App)

;; define multimethods for the rest of your components, e.g.
(defn not-found []
  (dom/h1 nil "This is the not-found page")) ;; -> functions are valid

;; don't forget the corresponding dispatch multimethod
(defmethod find-component :not-found [_] not-found)


;;7. Define your reconciler
(defonce reconciler
  (om/reconciler
   {:state app-state
    :parser (om/parser {:read read :mutate mutate})}))

(defn routerDidMount [router]
  (om/transact! router '[(launch-fireworks!)]))

;;8. jack a Router component with a dispatch multimethod into the reconciler & fire the thing up
(om/add-root!
 reconciler (router/Router {:dispatch find-component ;;required
                            :componentDidMount routerDidMount ;;optional
                            })
 (gdom/getElement "app"))

;;9. 

```


### On the nature of Queries...

There is some automatic query aggregation going on behind the scenes
to compute valid queries.
Keep in mind for valid query composition, the router will compose
the queries of it's children with joins. 

Example:
```clojure
;; component App
(defui App
  static om/IQuery
  [:app/title {:navbar/items (om/get-query NavBar)}])

;; this will produce the following root query if the handler defined in your routes
;; for component App is `:app`

[{:router [ router specific stuff]} {:app [:app/title {:navbar/items (om/get-query NavBar)}]}]

;; You have to call your parser recursively on :app...
(defmethod read :default
  [{:keys [parser query state] :as env} key params]
  (if (some #{key} [:app :home :about :not-found]) ;; -> usually all my handlers go in here
    ;; recursively call the parser,
    ;; i.e. we ignore :app || :home || :about || :not-found
    ;; and walk a little deeper in the query
    {:value (parser env query)}
    {:value (get @state key)}))

```

### Router Query

```clojure
;; router-stuff you can query 
[{:router [:route/url
           :route/pathname
           :route/query-params
           :route/params ;; think /users/:id -> :id 123
           :route/components
           :route/action]}]```


### Lifecycle hooks

```clojure

;; :onEnter
;; onEnter will receive the whole state and a replace function
;; return a state
;; if you need to replace the url, use (replace state "/some-path")
;; which returns the state with the path replaced by the new one
(defn authenticated [state replace]
  (when-let [user (:current-user state)]
    state
    (replace state "/login")))


;; :onLeave
;; return false or true, false will block the transition
(defn are-you-sure [state reconciler]
  ;;check if stuff is saved else... 
  (om/transact! reconciler '[(error-message!)])
  false)

```

### Route matching


```clojure
"/user/:name" ;; matches /user/alex and give you a :name param
"/user(/:name)" ;;  matches /user & /user/alex
"/files/**/*.jpg " ;; matches  /files/long/path/name/to/whatever.jpg
;; etc. need to document this more

```


### Manipulating La Historia

```clojure
(push! some-component-or-reconciler "/new-path")
(replace! some-component-or-reconciler "/new-path")

;;if you want the corresponding mutation queries...
(get-push-query "/new-path")
(get-replace-query "/new-path")

```


### Some things that might come in handy


```clojure
;; there is a link function available which produces <a></a>'s

(defui Some-Component
  Object
  (render [this]
          (dom/div nil
                   (om-router.core/link this
                                        {:className "class"
                                         :style {...}
                                         :href "/some-path"}))))

;; how do I normalize my initial app-state if the query isn't on screen yet?

(defmethod mutate 'load/it
  [{:keys [state component]} key {:keys [data]}]
  {:action #(swap! state (fn [st]
                           (merge st
                                  (om/tree->db component data true))))})


(def nav {:navbar/items [{:id 0 :name "hello" :path "/hello"}
                         {:id 1 :name "param-heaven" :path "/test/123/test/456"}
                         {:id 3 :name "home" :path "/home"}]})

(defui App
  static om/IQuery
  (query [this]
         [{:router [:route/pathname]} :app/title {:navbar/items (om/get-query MenuItem)}])
  Object
  (componentDidMount [this]
                     (om/transact! this `[(load/it {:data ~nav})]))
  (render ...))

;; how do I use the onEnter hook for authentication?
;; todo, will write a complete example.

```
