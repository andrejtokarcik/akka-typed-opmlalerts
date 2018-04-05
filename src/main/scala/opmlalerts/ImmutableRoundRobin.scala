package opmlalerts

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors

// Based on https://github.com/patriknw/akka-typed-blog/blob/master/src/main/scala/blog/typed/scaladsl/ImmutableRoundRobin.scala

object ImmutableRoundRobin {

  def roundRobinBehavior[T](numberOfWorkers: Int, worker: Behavior[T]): Behavior[T] =
    Behaviors.setup { ctx ⇒
      val workers = (1 to numberOfWorkers).map { n ⇒
        ctx.spawn(worker, s"worker-$n")
      }
      activeRoutingBehavior(index = 0, workers)
    }

  private def activeRoutingBehavior[T](index: Long, workers: Seq[ActorRef[T]]): Behavior[T] =
    Behaviors.immutable[T] { (ctx, msg) ⇒
      workers((index % workers.size).toInt) ! msg
      activeRoutingBehavior(index + 1, workers)
    }
}
