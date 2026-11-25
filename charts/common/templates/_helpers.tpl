{{/*
What every service's chart shares. A service chart's templates are one line each, calling these, so the
hardening, labels and probes are the same everywhere and the policy scan checks one place.
*/}}

{{- define "common.name" -}}
{{- .Values.nameOverride | default .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "common.selectorLabels" -}}
app.kubernetes.io/name: {{ include "common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "common.labels" -}}
{{ include "common.selectorLabels" . }}
app.kubernetes.io/part-of: skill2career
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/* By digest when there is one, the one the pipeline tested, so what runs is what was tested. */}}
{{- define "common.image" -}}
{{- $image := .Values.image -}}
{{- if $image.digest -}}
{{ $image.repository }}@{{ $image.digest }}
{{- else -}}
{{ $image.repository }}:{{ required "image.tag or image.digest is required" $image.tag }}
{{- end -}}
{{- end -}}

{{/* Nothing runs as root, writes outside the directories it is given, or keeps any capability. */}}
{{- define "common.podSecurityContext" -}}
runAsNonRoot: true
# Above 10000, so it cannot be the same as a user on the node. The images' files are readable by all, and
# what they write goes to directories given here, which belong to this group.
runAsUser: 10001
runAsGroup: 10001
fsGroup: 10001
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{- define "common.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
privileged: false
capabilities:
  drop: ["ALL"]
{{- end -}}

{{- define "common.env" -}}
{{- range $name, $value := .Values.env }}
- name: {{ $name }}
  value: {{ $value | quote }}
{{- end }}
{{- range .Values.secretEnv }}
- name: {{ .name }}
  valueFrom:
    secretKeyRef:
      name: {{ .secret }}
      key: {{ .key }}
{{- end }}
{{- end -}}

{{- define "common.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicas }}
  {{- end }}
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      {{- include "common.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "common.labels" . | nindent 8 }}
    spec:
      automountServiceAccountToken: false
      securityContext:
        {{- include "common.podSecurityContext" . | nindent 8 }}
      containers:
        - name: {{ include "common.name" . }}
          image: {{ include "common.image" . }}
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: {{ .Values.port }}
          env:
            {{- include "common.env" . | nindent 12 }}
          securityContext:
            {{- include "common.containerSecurityContext" . | nindent 12 }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          startupProbe:
            httpGet:
              path: {{ .Values.probes.startup }}
              port: http
            periodSeconds: 5
            failureThreshold: 60
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness }}
              port: http
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness }}
              port: http
            periodSeconds: 20
            failureThreshold: 3
          volumeMounts:
            {{- range $index, $path := .Values.writableDirs }}
            - name: writable-{{ $index }}
              mountPath: {{ $path }}
            {{- end }}
      volumes:
        {{- range $index, $path := .Values.writableDirs }}
        - name: writable-{{ $index }}
          emptyDir:
            sizeLimit: 256Mi
        {{- end }}
{{- end -}}

{{- define "common.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  selector:
    {{- include "common.selectorLabels" . | nindent 4 }}
  ports:
    - name: http
      port: {{ .Values.port }}
      targetPort: http
{{- end -}}

{{- define "common.autoscaling" -}}
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "common.name" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.cpuPercent }}
{{- end }}
{{- end -}}

{{/* Only with more than one, as a budget for a single pod would block every drain. */}}
{{- define "common.disruptionBudget" -}}
{{- if or .Values.autoscaling.enabled (gt (int .Values.replicas) 1) }}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  minAvailable: 1
  selector:
    matchLabels:
      {{- include "common.selectorLabels" . | nindent 6 }}
{{- end }}
{{- end -}}

{{/*
Nothing in or out but what the values allow, and dns. `ingress` and `egress` are the rules between this
release's own services, which the umbrella chart sets. `extraIngress` and `extraEgress` are an
environment's, as where the gateway, the database and keycloak are differs between a cluster and a cloud.
*/}}
{{- define "common.networkPolicy" -}}
{{- if .Values.networkPolicy.enabled }}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  podSelector:
    matchLabels:
      {{- include "common.selectorLabels" . | nindent 6 }}
  policyTypes: [Ingress, Egress]
  ingress:
    {{- range concat .Values.networkPolicy.ingress .Values.networkPolicy.extraIngress }}
    - from:
        {{- toYaml .from | nindent 8 }}
      ports:
        - port: {{ $.Values.port }}
    {{- end }}
  egress:
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - port: 53
          protocol: UDP
        - port: 53
          protocol: TCP
    {{- with concat .Values.networkPolicy.egress .Values.networkPolicy.extraEgress }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
{{- end }}
{{- end -}}

{{/* A route from the gateway the platform runs, for the services people reach. */}}
{{- define "common.route" -}}
{{- if .Values.route.enabled }}
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: {{ include "common.name" . }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  parentRefs:
    - name: {{ required "route.gateway.name is required: the gateway the platform runs" .Values.route.gateway.name }}
      {{- with .Values.route.gateway.namespace }}
      namespace: {{ . }}
      {{- end }}
      {{- with .Values.route.gateway.sectionName }}
      sectionName: {{ . }}
      {{- end }}
  {{- with .Values.route.hostnames }}
  hostnames:
    {{- toYaml . | nindent 4 }}
  {{- end }}
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: {{ .Values.route.path }}
      {{- with .Values.route.filters }}
      filters:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      backendRefs:
        - name: {{ include "common.name" . }}
          port: {{ .Values.port }}
{{- end }}
{{- end -}}
