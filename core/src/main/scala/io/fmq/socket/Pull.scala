package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Pull[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
) extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  def bind(protocol: tcp.HostPort): Resource[F, ConsumerSocket[F]] =
    Bind.bind[F](protocol, socket, blocker).as(new ConsumerSocket(socket, protocol.port))

  def bindToRandomPort(protocol: tcp.Host): Resource[F, ConsumerSocket[F]] =
    for {
      port <- Bind.bindToRandomPort[F](protocol, socket, blocker)
    } yield new ConsumerSocket(socket, port)

}