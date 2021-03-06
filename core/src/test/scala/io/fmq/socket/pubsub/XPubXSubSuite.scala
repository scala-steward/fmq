package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource}
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.frame.Frame
import io.fmq.syntax.literals._
import weaver.Expectations

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
object XPubXSubSuite extends ContextSuite {

  test("topic pub sub") { ctx =>
    withSockets(ctx) { case Pair(pub, sub) =>
      for {
        _      <- IO.sleep(200.millis)
        _      <- sub.sendSubscribe(Subscriber.Topic.utf8String("A"))
        _      <- IO.sleep(200.millis)
        subMsg <- pub.receive[Array[Byte]]
        _      <- pub.sendMultipart(Frame.Multipart("A", "Hello"))
        msg    <- sub.receiveFrame[String]
      } yield expect(subMsg.sameElements(Array[Byte](XSubscriber.Subscribe, 'A'))) and
        expect(msg == Frame.Multipart("A", "Hello"))
    }
  }

  test("census") { ctx =>
    withSockets(ctx) { case Pair(pub, sub) =>
      for {
        _    <- IO.sleep(200.millis)
        _    <- sub.send("Message from subscriber")
        msg1 <- pub.receive[String]
        _    <- sub.send(Array.emptyByteArray)
        msg2 <- pub.receive[Array[Byte]]
      } yield expect(msg1 == "Message from subscriber") and expect(msg2.isEmpty)
    }
  }

  test("simple pub sub") { ctx =>
    withSockets(ctx) { case Pair(pub, sub) =>
      for {
        _   <- IO.sleep(200.millis)
        _   <- sub.sendSubscribe(Subscriber.Topic.All)
        _   <- IO.sleep(200.millis)
        _   <- pub.send("Hello")
        msg <- sub.receive[String]
      } yield expect(msg == "Hello")
    }
  }

  test("not subscribed") { ctx =>
    withSockets(ctx) { case Pair(pub, sub) =>
      for {
        _      <- IO.sleep(200.millis)
        _      <- pub.send("Hello")
        result <- sub.receiveNoWait[String]
      } yield expect(result.isEmpty)
    }
  }

  test("multiple subscriptions") { ctx =>
    withSockets(ctx) { case Pair(pub, sub) =>
      val topics   = List("A", "B", "C", "D", "E")
      val messages = topics.map(_ + "1")

      for {
        _        <- IO.sleep(200.millis)
        _        <- topics.traverse(topic => sub.sendSubscribe(Subscriber.Topic.utf8String(topic)))
        _        <- IO.sleep(200.millis)
        _        <- messages.traverse(pub.send[String])
        received <- Stream.repeatEval(sub.receive[String]).take(5).compile.toList
        _        <- topics.traverse(topic => sub.sendUnsubscribe(Subscriber.Topic.utf8String(topic)))
        _        <- IO.sleep(200.millis)
        _        <- messages.traverse(pub.send[String])
        result   <- sub.receiveNoWait[String]
      } yield expect(received == messages) and expect(result.isEmpty)
    }
  }

  test("multiple subscribers") { ctx =>
    val uri = tcp"://localhost:53123"

    val topics1 = List("A", "AB", "B", "C")
    val topics2 = List("A", "AB", "C")

    def program(input: (XPublisher.Socket[IO], XSubscriber.Socket[IO], XSubscriber.Socket[IO])): IO[Expectations] = {
      val (pub, sub1, sub2) = input

      for {
        _    <- IO.sleep(200.millis)
        _    <- topics1.traverse(topic => sub1.sendSubscribe(Subscriber.Topic.utf8String(topic)))
        _    <- topics2.traverse(topic => sub2.sendSubscribe(Subscriber.Topic.utf8String(topic)))
        _    <- IO.sleep(200.millis)
        _    <- pub.send("AB-1")
        msg1 <- sub1.receive[String]
        msg2 <- sub2.receive[String]
      } yield expect(msg1 == "AB-1") and expect(msg2 == "AB-1")
    }

    (for {
      producer  <- Resource.suspend(ctx.createXPublisher.map(_.bind(uri)))
      consumer1 <- Resource.suspend(ctx.createXSubscriber.map(_.connect(uri)))
      consumer2 <- Resource.suspend(ctx.createXSubscriber.map(_.connect(uri)))
    } yield (producer, consumer1, consumer2)).use(program)
  }

  private def withSockets[A](ctx: Context[IO])(fa: XPubXSubSuite.Pair[IO] => IO[A]): IO[A] = {
    val uri = tcp_i"://localhost"

    (for {
      producer <- Resource.suspend(ctx.createXPublisher.map(_.bindToRandomPort(uri)))
      consumer <- Resource.suspend(ctx.createXSubscriber.map(_.connect(producer.uri)))
    } yield XPubXSubSuite.Pair(producer, consumer)).use(fa)
  }

  private final case class Pair[F[_]](
      publisher: XPublisher.Socket[F],
      subscriber: XSubscriber.Socket[F]
  )
}
