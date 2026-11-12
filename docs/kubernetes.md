# Kubernetes

Docs: https://kubernetes.io/docs/concepts/ , Spring Boot on Kubernetes:
https://docs.spring.io/spring-boot/how-to/deployment/cloud.html#howto.deployment.cloud.kubernetes

## Objects used (`k8s/`)

- Deployment: stateless pods, rolling updates (order-service ×2, product-service ×1, Schema Registry).
- StatefulSet: stable name and its own volume per pod (Postgres with a PVC, Kafka).
- Service: stable DNS name and load balancing over ready pods (`order-service:8081`, `postgres:5432`...).
- ConfigMap: non-secret config as env vars (`SPRING_DATASOURCE_URL`, `SPRING_PROFILES_ACTIVE=k8s`...).
- Secret: base64, not encryption. Readable by anyone with access to the namespace. Real setups use sealed
  secrets / external secret stores. Here a `secretGenerator` with the local default password.
- Kustomize: `kubectl apply -k k8s/`, built into kubectl. Generators add a hash suffix to the Secret name, so a
  changed secret rolls the pods.
- `enableServiceLinks: false`: otherwise every Service adds env vars like `KAFKA_PORT=tcp://...` to pods, and
  the Kafka and Schema Registry images treat `KAFKA_*` / `SCHEMA_REGISTRY_*` as their own config.

## Image

- Multi-stage: build stage with JDK + `./mvnw package`, runtime stage with JRE only.
- `java -Djarmode=tools -jar app.jar extract --layers --launcher`: dependencies, loader, snapshot deps,
  application in separate layers. Code change → only the small layer is rebuilt and pushed.
  https://docs.spring.io/spring-boot/reference/packaging/container-images/efficient-images.html
- BuildKit cache mount for `~/.m2`: first build ~3.5 min here, the second ~10 s.
- Non-root user with a numeric uid (`runAsNonRoot` can only verify numeric ids).
- `-XX:MaxRAMPercentage=75`: heap = 75% of the container memory limit, the rest for metaspace, threads,
  direct buffers.
- Alternatives: Jib (builds layered images from Maven without Docker), Buildpacks (`spring-boot:build-image`,
  no Dockerfile at all). Both fine; a Dockerfile shows every step.

## Probes

https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/

- startup: until it passes, the other probes are off. Here liveness endpoint, 30 × 5 s.
- liveness: fails → container restarted. Only the app itself (`livenessState`). A dependency in liveness turns
  a database outage into restarts of every pod.
- readiness: fails → pod removed from Service endpoints, not restarted. App + `db`. No Kafka: the outbox lets
  us accept orders without it.
- Spring: `management.endpoint.health.probes.enabled`, groups in `application.yml`, `HealthProbesTest` checks
  the group contents.

## Graceful shutdown

https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination

1. Pod gets deleted: preStop hook runs, and in parallel the endpoints controller removes the pod from Services.
   kube-proxy/ingress learn it a bit later, so requests can still arrive.
2. `preStop: sleep 5` (native sleep action, no shell needed in the image) waits that out.
3. SIGTERM → `server.shutdown=graceful` stops accepting and waits for running requests
   (`spring.lifecycle.timeout-per-shutdown-phase=20s`); listener containers stop and commit; the scheduler
   waits for the relay batch (`spring.task.scheduling.shutdown.await-termination`).
4. `terminationGracePeriodSeconds: 30` > 5 + 20, otherwise SIGKILL comes first.

Rolling update: `maxUnavailable: 0`, `maxSurge: 1` → new pod ready before an old one goes.

## Resources

- requests = what the scheduler reserves; limits = hard cap. Memory over limit → OOMKilled.
- CPU limit → CFS throttling. JVM startup (JIT, class loading) needs a burst; a low limit stretches startup
  and can fail the startup probe. Argument for limits: noisy neighbours, predictable cost. Common compromise:
  CPU request, no CPU limit, memory request + limit. Without a CPU limit the JVM sees all node CPUs, sizes GC
  threads on that; `-XX:ActiveProcessorCount` pins it if needed.
- `readOnlyRootFilesystem` + `emptyDir` for `/tmp` (embedded Tomcat keeps its work directory there).

## Helm

- Templates + values + releases with rollback. Worth it for charts reused across environments or shared with
  others. For two services in one local cluster Kustomize overlays are enough.
