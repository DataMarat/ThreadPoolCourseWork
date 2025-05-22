# Курсовая работа: Кастомный пул потоков (Thread Pool)
Этот проект реализует собственный пул потоков для высоконагруженных приложений без использования ThreadPoolExecutor. Поддерживается конфигурируемое управление потоками, ограниченная очередь задач, логирование событий, и стратегия отказа при перегрузке.
Цель проекта — получить глубокое понимание принципов многопоточности, балансировки нагрузки и проектирования отказоустойчивых систем.

## Структура проекта


```agsl
ThreadPoolCourseWork/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── org/
        │       └── example/
        │           └── threadpool/
        │               ├── demo/
        │               │   └── Main.java                    # Демонстрационная программа
        │               │
        │               ├── executor/
        │               │   ├── CustomExecutor.java          # Интерфейс пула
        │               │   └── CustomThreadPool.java        # Основной класс пула
        │               │
        │               ├── internal/
        │               │   ├── Worker.java                  # Поток-исполнитель задач
        │               │   ├── ThreadFactoryImpl.java       # Фабрика именованных потоков
        │               │   └── TaskQueue.java               # Обёртка над очередью задач
        │               │
        │               └── policy/
        │                   ├── RejectionPolicy.java         # Интерфейс отказа при переполнении
        │                   └── AbortPolicy.java             # Реализация: отказ с логированием
        │
        └── resources/
            └── logback.xml (опционально)                    # Конфигурация логгера

```

## Анализ производительности

Для оценки эффективности собственного пула потоков была проведена практическая нагрузка и сравнительный анализ со стандартным `ThreadPoolExecutor` и промышленными решениями, такими как Tomcat и Jetty.

### Сравнение с `ThreadPoolExecutor` (JDK)

| Критерий                        | CustomThreadPool                             | ThreadPoolExecutor                        |
|----------------------------------|----------------------------------------------|-------------------------------------------|
| Гибкость настройки              | Полный контроль над очередью, потоками и логикой отказа | Хорошая, но с предопределённым поведением |
| Логирование                     | Полное, настраиваемое                        | Требует обёртки                            |
| Балансировка задач             | Round-robin/очередь                          | Очередь + внутренний планировщик          |
| Отказоустойчивость             | Зависит от политики (`AbortPolicy` и др.)   | Поддержка `RejectedExecutionHandler`      |
| Порог адаптивного масштабирования | Управляется вручную                         | Встроенное поведение                      |
| Наглядность и отладка          | Высокая, благодаря логам                     | Ограниченная, без дополнительных средств  |

**Вывод**: при учебных и исследовательских задачах кастомная реализация показывает лучшее понимание механики. В реальных проектах `ThreadPoolExecutor` предпочтительнее из-за высокой оптимизации и безопасности.

### Сравнение с Tomcat/Jetty

Обе серверные платформы используют собственные реализации пула потоков с расширенной поддержкой:

- **Tomcat** использует `org.apache.tomcat.util.threads.ThreadPoolExecutor` — модифицированный и адаптированный JDK-пул.
- **Jetty** применяет `QueuedThreadPool` — с балансировкой, адаптивной стратегией и thread affinity.

В отличие от них, текущая реализация работает с одной глобальной очередью (что упрощает архитектуру) 
и не использует оптимизации типа повторного использования потоков (reuse) или обработки I/O в рамках пула.
При этом данное решение демонстрирует наглядную структуру, простоту расширения и прозрачную логику управления.

## Исследование параметров производительности

В рамках демонстрационной программы были протестированы различные конфигурации параметров пула. Основная цель — выявить, какие настройки обеспечивают наибольшую пропускную способность при ограниченной нагрузке и избежать избыточного создания потоков.

### Методика

- Каждая задача симулирует нагрузку с помощью `Thread.sleep(3000)` и логирует начало/окончание.
- Общее число отправленных задач: 10.
- Измерялись количество отклонённых задач, число активных потоков и скорость завершения всех задач.
- Менялись параметры:
    - `corePoolSize`
    - `maxPoolSize`
    - `queueSize`
    - `keepAliveTime`
    - `minSpareThreads`

### Результаты

| Конфигурация                                  | Поведение                                                |
|----------------------------------------------|----------------------------------------------------------|
| `core=2`, `max=4`, `queue=5`, `keepAlive=5s` | На 10 задач: 9 обработаны, 1 отклонена, 4 потока созданы |
| `core=4`, `max=4`, `queue=2`, `keepAlive=5s` | 6 задач сразу обрабатываются, остальные — отклонены      |
| `core=2`, `max=6`, `queue=8`, `keepAlive=10s`| Все 10 задач обработаны, 6 потоков активно               |
| `core=1`, `max=2`, `queue=1`, `keepAlive=3s` | Часть задач отбрасывается, задержки, медленная обработка |

### Выводы

- Увеличение `queueSize` помогает при коротких задачах, но может вызывать задержки при длинных.
- `minSpareThreads` полезен для постоянной готовности к всплеску нагрузки.
- Слишком большой `keepAliveTime` замедляет высвобождение ресурсов.
- Значения `corePoolSize` и `maxPoolSize` следует подбирать с учётом типов задач (CPU-bound или IO-bound).

Рекомендуется начинать с равных значений `corePoolSize = maxPoolSize` и минимальной очереди, затем адаптировать под характер нагрузки.

## Принцип распределения задач и балансировки

В данной реализации применяется алгоритм балансировки задач по принципу **Round Robin**. Каждому рабочему потоку (`Worker`) соответствует своя **персональная очередь задач**. Все очереди управляются пулом, а задачи равномерно распределяются по ним.

### Механизм работы

1. При инициализации каждый `Worker` получает свою очередь (тип `BlockingQueue`).
2. Все очереди хранятся в списке `workerQueues`.
3. Метод `execute()` направляет задачи по кругу (с помощью `AtomicInteger roundRobinIndex`) в следующую очередь.
4. Каждый `Worker` извлекает задачи только из своей очереди.
5. Поток завершает работу, если не получает задач в течение `keepAliveTime`, и общее число потоков превышает `corePoolSize`.
6. При завершении поток удаляется вместе со своей очередью.

### Почему выбран Round Robin

- Требование ТЗ: реализовать алгоритм распределения (например, Round Robin).
- Алгоритм прост, прозрачен и предсказуем.
- Обеспечивает равномерную загрузку потоков при высокой частоте задач.
- Легко расширяется для мониторинга и отладки.

### Возможности расширения

- Использование стратегии Least Loaded (по размеру очередей).
- Поддержка приоритетов задач.
- Отдельные пулы для разных типов задач (CPU-bound / IO-bound).

## Вывод демо-программы
```bash
21:15:33.291 [main] INFO  o.e.t.internal.ThreadFactoryImpl -- [ThreadFactory] Creating new thread: MyPool-worker-1
21:15:33.295 [main] INFO  o.e.t.internal.ThreadFactoryImpl -- [ThreadFactory] Creating new thread: MyPool-worker-2
21:15:33.295 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [ThreadFactory] Creating new thread: MyPool-worker-1
21:15:33.295 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [ThreadFactory] Creating new thread: MyPool-worker-2
21:15:33.295 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #0: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@35083305
21:15:33.296 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@35083305
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #1: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@8e0379d
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #0: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@341b80b2
21:15:33.296 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@8e0379d
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #1: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@55a1c291
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #0: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@2145433b
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #1: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@2890c451
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #0: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@40e6dfe1
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #1: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@1b083826
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #0: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@105fece7
21:15:33.296 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Task accepted into queue #1: org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@3ec300f1
[Task 1] Started in MyPool-worker-1
[Task 2] Started in MyPool-worker-2
[Task 2] Finished in MyPool-worker-2
[Task 1] Finished in MyPool-worker-1
21:15:36.310 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@55a1c291
21:15:36.310 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@341b80b2
[Task 4] Started in MyPool-worker-2
[Task 3] Started in MyPool-worker-1
[Task 3] Finished in MyPool-worker-1
[Task 4] Finished in MyPool-worker-2
21:15:39.320 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@2890c451
[Task 6] Started in MyPool-worker-2
21:15:39.320 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@2145433b
[Task 5] Started in MyPool-worker-1
[Task 5] Finished in MyPool-worker-1
[Task 6] Finished in MyPool-worker-2
21:15:42.327 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@1b083826
[Task 8] Started in MyPool-worker-2
21:15:42.327 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 executes org.example.threadpool.demo.Main$$Lambda/0x000001934e0ba6b0@40e6dfe1
[Task 7] Started in MyPool-worker-1
21:15:43.310 [main] INFO  o.e.t.executor.CustomThreadPool -- [Pool] Shutdown initiated.
[Task 8] Finished in MyPool-worker-2
[Task 7] Finished in MyPool-worker-1
21:15:45.341 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 skipping task due to shutdown.
21:15:45.341 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 skipping task due to shutdown.
[Main] Demo complete.
21:15:50.344 [MyPool-worker-2] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-2 idle timeout, stopping.
21:15:50.344 [MyPool-worker-1] INFO  o.example.threadpool.internal.Worker -- [Worker] MyPool-worker-1 idle timeout, stopping.
21:15:50.344 [MyPool-worker-2] INFO  o.e.t.executor.CustomThreadPool -- [Worker] MyPool-worker-2 terminated.
21:15:50.344 [MyPool-worker-1] INFO  o.e.t.executor.CustomThreadPool -- [Worker] MyPool-worker-1 terminated.

Process finished with exit code 0

```