package io.fmq.poll

import java.nio.channels.Selector

import cats.effect.concurrent.Ref
import cats.effect.{Resource, Sync}
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.fmq.{ConsumerSocket, ProducerSocket}
import org.zeromq.ZContext
import zmq.poll.{PollItem => ZPollItem}

final class Poller[F[_]: Sync] private (itemsRef: Ref[F, Set[PollEntry[F]]], selector: Selector) {

  def registerConsumer(socket: ConsumerSocket[F], handler: ConsumerHandler[F]): F[Unit] =
    itemsRef.update(_ + PollEntry.Read(socket, handler))

  def registerProducer(socket: ProducerSocket[F], handler: ProducerHandler[F]): F[Unit] =
    itemsRef.update(_ + PollEntry.Write(socket, handler))

  def poll(timeout: PollTimeout): F[Int] =
    for {
      items   <- itemsRef.get
      polling <- items.map(item => (item, toZmqPollItem(item))).toMap.pure[F]
      events  <- Sync[F].delay(zmq.ZMQ.poll(selector, polling.values.toArray, items.size, timeout.value))
      _       <- polling.toList.traverse((dispatchItem _).tupled)
    } yield events

  private def dispatchItem(entity: PollEntry[F], item: ZPollItem): F[Unit] = {
    val availableEvents = item.readyOps()

    Sync[F].whenA(availableEvents > 0) {
      entity match {
        case PollEntry.Read(socket, handler)  => handler.run(socket)
        case PollEntry.Write(socket, handler) => handler.run(socket)
      }
    }
  }

  private def toZmqPollItem(pollItem1: PollEntry[F]): ZPollItem =
    pollItem1 match {
      case PollEntry.Read(socket, _)  => new ZPollItem(socket.socket.base, zmq.ZMQ.ZMQ_POLLIN)
      case PollEntry.Write(socket, _) => new ZPollItem(socket.socket.base, zmq.ZMQ.ZMQ_POLLOUT)
    }

}

object Poller {

  def create[F[_]: Sync](ctx: ZContext): Resource[F, Poller[F]] =
    for {
      selector <- Resource.fromAutoCloseable(Sync[F].delay(ctx.getContext.selector()))
      ref      <- Resource.liftF(Ref.of[F, Set[PollEntry[F]]](Set.empty))
    } yield new Poller[F](ref, selector)

}
