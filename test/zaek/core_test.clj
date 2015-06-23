(ns zaek.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
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


;; (deftest experiments1
;;   (with-open [conn (zk/connect)
;;               ch1  (zk/channel conn)
;;               ch2  (zk/channel conn)]
;;     (zk/declare-queue! ch1 :q1)
;;     (zk/bind-queue! ch1 :q1 "amq.topic" "foo.bar")
;;     (zk/consume ch1 :q1 false (fn [tag env props data]
;;                                 (println "received:" tag (vec data))
;;                                 (a/thread
;;                                   (a/<!! (a/timeout 1000))
;;                                   (zk/ack ch1 tag))))
;;     (let [d (into-array Byte/TYPE [1 2 3])]
;;       (zk/publish ch2 "amq.topic" "foo.bar" d)
;;       (zk/publish ch2 "amq.topic" "foo.bar" d))
;;     (a/<!! (a/timeout 4000)))
;;   )

;; (deftest experiments2
;;   (with-open [conn (zk/connect)
;;               ch1  (zk/channel conn)
;;               ch2  (zk/channel conn)]
;;     (zk/declare-queue! ch1 :q1)
;;     (zk/bind-queue! ch1 :q1 "amq.topic" "foo.bar")
;;     (zk/consume ch1 :q1 false (fn [tag env props data]
;;                                 (println "received1:" tag (vec data))
;;                                 (a/thread
;;                                   (a/<!! (a/timeout 5000))
;;                                   (zk/ack ch1 tag))))
;;     (zk/declare-queue! ch1 :q1)
;;     (zk/bind-queue! ch1 :q1 "amq.topic" "foo.bar")
;;     (zk/consume ch1 :q1 false (fn [tag env props data]
;;                                 (println "received2:" tag (vec data))
;;                                 (a/thread
;;                                   (a/<!! (a/timeout 5000))
;;                                   (zk/ack ch1 tag))))
;;     (let [d (into-array Byte/TYPE [1 2 3])]
;;       (zk/publish ch2 "amq.topic" "foo.bar" d)
;;       (zk/publish ch2 "amq.topic" "foo.bar" d))
;;     (a/<!! (a/timeout 40000)))
;;   )
