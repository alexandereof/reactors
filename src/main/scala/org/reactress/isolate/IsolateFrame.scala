package org.reactress
package isolate



import scala.util.control.NonFatal



final class IsolateFrame[@spec(Int, Long, Double) T, @spec(Int, Long, Double) Q](
  val eventQueue: EventQueue[Q],
  val sourceEmitter: Reactive.Emitter[Q],
  val failureEmitter: Reactive.Emitter[Throwable],
  val scheduler: Scheduler,
  val state: IsolateFrame.State
) extends Reactor[T] with (Q => Unit) {
  @volatile private[reactress] var isolate: ReactIsolate[T, Q] = _
  @volatile private[reactress] var channel: Channel[T] = _

  val errorHandling: PartialFunction[Throwable, Unit] = {
    case NonFatal(t) => failureEmitter += t
  }

  def apply(event: Q) {
    try sourceEmitter += event
    catch errorHandling
    finally {}
  }

  def react(event: T) {
    isolate.later += event
  }

  def isOwned: Boolean = state.READ_STATE == 1

  final def tryOwn(): Boolean = state.CAS_STATE(0, 1)

  final def unOwn(): Unit = state.WRITE_STATE(0)

  def unreact() {
    // TODO channel has been closed, so no new messages will be added to the event queue
  }

  def init(dummy: IsolateFrame[T, Q]) {
    // call the asynchronous foreach on the event queue
    eventQueue.foreach(this)(scheduler)
  }

  init(this)

  def run() {
    try {
      isolateAndRun()
    } finally {
      unOwn()
      if (eventQueue.nonEmpty) {
        if (tryOwn()) scheduler.schedule(this)
      }
    }
  }

  private def isolateAndRun() {
    if (ReactIsolate.selfIsolate.get != null) {
      throw new IllegalStateException("Cannot execute isolate inside of another isolate.")
    }
    try {
      ReactIsolate.selfIsolate.set(isolate)
      runInIsolate()
    } catch scheduler.handler
    finally {
      ReactIsolate.selfIsolate.set(null)
    }
  }

  private def runInIsolate() {
    while (eventQueue.nonEmpty) {
      val event = eventQueue.dequeue()
      apply(event)
    }
  }

}


object IsolateFrame {

  final class State {
    @volatile private[reactress] var state: Int = 0

    def READ_STATE: Int = state

    def WRITE_STATE(v: Int): Unit = util.unsafe.putIntVolatile(this, IsolateFrame.STATE_OFFSET, v)

    def CAS_STATE(ov: Int, nv: Int): Boolean = util.unsafe.compareAndSwapInt(this, IsolateFrame.STATE_OFFSET, ov, nv)
  }

  val STATE_OFFSET = util.unsafe.objectFieldOffset(classOf[State].getDeclaredField("state"))

}
