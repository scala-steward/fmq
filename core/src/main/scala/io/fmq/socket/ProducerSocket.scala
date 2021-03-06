package io.fmq.socket

import cats.effect.kernel.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.fmq.frame.{Frame, FrameEncoder}
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}

trait ProducerSocket[F[_]] extends ConnectedSocket with SocketOptions[F] with CommonOptions.Get[F] with SendOptions.Get[F] {

  def sendFrame[A: FrameEncoder](frame: Frame[A]): F[Unit] =
    frame match {
      case Frame.Single(value)   => send(value)
      case m: Frame.Multipart[A] => sendMultipart(m)
    }

  def sendMultipart[A: FrameEncoder](frame: Frame.Multipart[A]): F[Unit] = {
    val parts = frame.parts

    for {
      _ <- parts.init.traverse(sendMore[A])
      _ <- send(parts.last)
    } yield ()
  }

  /**
    * Low-level API.
    *
    * Queues a message to be sent.
    *
    * The data is either a single-part message by itself, or the last part of a multi-part message.
    */
  def send[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.send(FrameEncoder[A].encode(value))).void

  /**
    * Low-level API.
    *
    * Queues a multi-part message to be sent.
    */
  def sendMore[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.sendMore(FrameEncoder[A].encode(value))).void

}

object ProducerSocket {

  abstract class Connected[F[_]](implicit protected val F: Sync[F]) extends ConnectedSocket with ProducerSocket[F]

}
