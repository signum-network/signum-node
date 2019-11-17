package brs.services.impl

import brs.objects.Constants
import brs.services.*
import brs.util.delegates.Atomic
import brs.util.logging.safeError
import brs.util.rxjava.RxJavaUtils
import brs.util.rxjava.toFuture
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Maybe
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Action
import io.reactivex.schedulers.Schedulers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future

class RxJavaTaskSchedulerService: TaskSchedulerService {
    private var started by Atomic(false) // Stays true after shutdown
    private val disposables = CompositeDisposable()

    // No sync as these should only be called during initialization
    private val beforeStartTasks = mutableListOf<Task>()
    private val afterStartTasks = mutableListOf<Task>()
    private val scheduledWithDelayTasks = mutableMapOf<Task, Triple<TaskType, Long, Long>>()
    private val scheduledTasks = mutableMapOf<RepeatingTask, TaskType>()
    private val scheduledParallelTasks = mutableMapOf<RepeatingTask, Pair<TaskType, Int>>()

    private fun requireStarted() {
        require(started) { "Task Scheduler has not yet started" }
    }

    private fun requireNotStarted() {
        require(!started) { "Task Scheduler has already started" }
    }

    override fun run(taskType: TaskType, task: Task) {
        requireStarted()
        runTask(taskToTask(task, taskType))
    }

    override fun <T : Any> async(taskType: TaskType, task: TaskWithResult<T>): Future<T?> {
        requireStarted()
        // TODO safe create
        val future = Maybe.fromCallable(task).subscribeOn(taskType.toScheduler())
            .toFuture()
        disposables.add(future)
        return future
    }

    override fun runBeforeStart(task: Task) {
        requireNotStarted()
        beforeStartTasks.add(task)
    }

    override fun runAfterStart(task: Task) {
        requireNotStarted()
        afterStartTasks.add(task)
    }

    override fun scheduleTaskWithDelay(taskType: TaskType, initialDelayMs: Long, delayMs: Long, task: Task) {
        requireNotStarted()
        scheduledWithDelayTasks[task] = Triple(taskType, initialDelayMs, delayMs)
    }

    override fun scheduleTask(taskType: TaskType, task: RepeatingTask) {
        requireNotStarted()
        scheduledTasks[task] = taskType
    }

    override fun scheduleTask(taskType: TaskType, numberOfInstances: Int, task: RepeatingTask) {
        requireNotStarted()
        scheduledParallelTasks[task] = Pair(taskType, numberOfInstances)
    }

    private fun runTask(task: Completable) {
        disposables.add(task.subscribe(emptyHandler, RxJavaUtils.defaultErrorHandler))
    }

    override fun awaitTasks(tasksType: TaskType, tasks: Iterable<Task>) {
        Completable.merge(tasks.map { taskToTask(it, tasksType) })
            .blockingAwait()
    }

    private inline fun safeCreateCompletable(crossinline task: (CompletableEmitter) -> Unit): Completable {
        return Completable.create {
            try {
                task(it)
            } catch (e: Exception) {
                if (e is InterruptedException) return@create
                if (it.isDisposed) {
                    logger.safeError(e) { "Error occurred after disposed" }
                } else {
                    it.onError(e)
                }
            }
            it.onComplete()
        }
    }

    private fun taskToTask(task: Task, taskType: TaskType): Completable {
        return safeCreateCompletable { task() }
            .subscribeOn(taskType.toScheduler())
    }

    private fun delayedRepeatingTaskToTask(task: Task, taskType: TaskType, initialDelayMs: Long, delayMs: Long): Completable {
        return safeCreateCompletable {
            Thread.sleep(initialDelayMs)
            while (!it.isDisposed) { // TODO catch stuff
                task()
                Thread.sleep(delayMs)
            }
        }
            .subscribeOn(taskType.toScheduler())
    }

    private fun repeatingTaskToTask(task: RepeatingTask, taskType: TaskType): Completable {
        return safeCreateCompletable {
            while (!it.isDisposed) { // TODO catch stuff
                if (!task()) {
                    Thread.sleep(Constants.TASK_FAILURE_DELAY_MS)
                }
            }
        }
            .subscribeOn(taskType.toScheduler())
    }

    override fun start() {
        requireNotStarted()
        started = true
        // Run before start tasks
        awaitTasks(TaskType.IO, beforeStartTasks)

        // Start regular scheduled tasks
        scheduledTasks.forEach { (task, taskType) -> runTask(repeatingTaskToTask(task, taskType)) }

        // Start parallel scheduled tasks
        scheduledParallelTasks.forEach { (task, taskInfo) ->
            val runner = repeatingTaskToTask(task, taskInfo.first)
            repeat(taskInfo.second) {
                runTask(runner)
            }
        }
        // Start delayed scheduled tasks
        scheduledWithDelayTasks.forEach { (task, taskInfo) -> runTask(delayedRepeatingTaskToTask(task, taskInfo.first, taskInfo.second, taskInfo.third)) }

        // Run after start tasks
        awaitTasks(TaskType.IO, afterStartTasks)
    }

    override fun shutdown() {
        if (!started) return
        started = false
        disposables.dispose()
    }

    private fun TaskType.toScheduler(): Scheduler {
        return when(this) {
            TaskType.IO -> Schedulers.io()
            TaskType.COMPUTATION -> Schedulers.computation()
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(RxJavaTaskSchedulerService::class.java)
        val emptyHandler: Action = Action { }
    }
}