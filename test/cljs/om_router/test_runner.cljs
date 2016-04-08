(ns om-router.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [om-router.core-test]))

(enable-console-print!)

(doo-tests 'om-router.core-test)
