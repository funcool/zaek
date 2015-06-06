(ns zaek.core-test
  (:require [clojure.test :refer :all]
            [zaek.core :as zk])
  (:import java.util.Arrays))

(deftest basic-tests
  (testing "Connect and publish and subscribe with named queues and direct exchange."
    (let [p (promise)
          d (into-array Byte/TYPE [1 2 3])]
      (with-open [conn (zk/connect)
                  ch1  (zk/channel conn)
                  ch2  (zk/channel conn)]
        (zk/declare-queue! ch1 :q1)
        (zk/bind-queue! ch1 :q1 "amq.direct" "foo.bar")
        (zk/consume ch1 :q1 (fn [tag env props data]
                              (deliver p data)))
        (zk/publish ch2 "amq.direct" "foo.bar" d)
        (is (Arrays/equals d (deref p 1000 nil))))))

  (testing "Connect and publish and subscribe with server-named queues and direct exchange."
    (let [p (promise)
          d (into-array Byte/TYPE [1 2 3])]
      (with-open [conn (zk/connect)
                  ch1  (zk/channel conn)
                  ch2  (zk/channel conn)]
        (let [queue (zk/declare-queue! ch1)]
          (zk/bind-queue! ch1 queue "amq.direct" "foo.bar")
          (zk/consume ch1 queue (fn [tag env props data]
                                  (deliver p data))))

        (zk/publish ch2 "amq.direct" "foo.bar" d)
        (is (Arrays/equals d (deref p 1000 nil)))))))
