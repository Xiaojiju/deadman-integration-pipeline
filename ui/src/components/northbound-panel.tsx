import { Loader2Icon } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { CodeSample, ConfigExample } from "@/components/config-example"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { catalogApi } from "@/lib/api"
import type { NorthboundView } from "@/lib/types"

const SECRET_MASK = "••••"

type FormState = {
  mqttEnabled: boolean
  mqttTransport: string
  mqttUrl: string
  mqttCommandTopic: string
  mqttResponseTopic: string
  mqttTelemetryTopic: string
  mqttClientId: string
  mqttUsername: string
  mqttPassword: string
  httpEnabled: boolean
  httpWebhookUrl: string
  httpTimeoutMs: string
  httpMaxAttempts: string
}

function toForm(view: NorthboundView): FormState {
  return {
    mqttEnabled: view.mqttEnabled,
    mqttTransport: view.mqttTransport || "paho",
    mqttUrl: view.mqttUrl ?? "",
    mqttCommandTopic: view.mqttCommandTopic ?? "gw/+/command",
    mqttResponseTopic: view.mqttResponseTopic ?? "gw/{deviceId}/response",
    mqttTelemetryTopic: view.mqttTelemetryTopic ?? "gw/{deviceId}/telemetry",
    mqttClientId: view.mqttClientId ?? "gateway-northbound",
    mqttUsername: view.mqttUsername ?? "",
    mqttPassword: view.mqttPasswordSet ? SECRET_MASK : (view.mqttPassword ?? ""),
    httpEnabled: view.httpEnabled,
    httpWebhookUrl: view.httpWebhookUrl ?? "",
    httpTimeoutMs: String(view.httpTimeoutMs ?? 3000),
    httpMaxAttempts: String(view.httpMaxAttempts ?? 2),
  }
}

export function NorthboundPanel() {
  const [view, setView] = useState<NorthboundView | null>(null)
  const [form, setForm] = useState<FormState | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const next = await catalogApi.getNorthbound()
      setView(next)
      setForm(toForm(next))
      setError(null)
    } catch (err) {
      const message = err instanceof Error ? err.message : "无法读取北向配置"
      setError(message)
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const save = async () => {
    if (!form) {
      return
    }
    const timeoutMs = Number(form.httpTimeoutMs)
    const maxAttempts = Number(form.httpMaxAttempts)
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
      toast.error("Webhook 超时必须大于 0")
      return
    }
    if (!Number.isFinite(maxAttempts) || maxAttempts <= 0) {
      toast.error("Webhook 重试次数必须大于 0")
      return
    }
    setSaving(true)
    try {
      const next = await catalogApi.updateNorthbound({
        mqttEnabled: form.mqttEnabled,
        mqttTransport: form.mqttTransport,
        mqttUrl: form.mqttUrl,
        mqttCommandTopic: form.mqttCommandTopic,
        mqttResponseTopic: form.mqttResponseTopic,
        mqttTelemetryTopic: form.mqttTelemetryTopic,
        mqttClientId: form.mqttClientId,
        mqttUsername: form.mqttUsername,
        mqttPassword: form.mqttPassword,
        httpEnabled: form.httpEnabled,
        httpWebhookUrl: form.httpWebhookUrl,
        httpTimeoutMs: timeoutMs,
        httpMaxAttempts: maxAttempts,
      })
      setView(next)
      setForm(toForm(next))
      setError(null)
      if (next.mqttError) {
        toast.warning("配置已保存，北向 MQTT 未接通：" + next.mqttError)
      } else {
        toast.success("北向配置已保存并生效")
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "保存北向配置失败"
      toast.error(message)
    } finally {
      setSaving(false)
    }
  }

  if (loading && !form) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2Icon className="size-4 animate-spin" />
        正在加载北向配置
      </div>
    )
  }

  if (!form) {
    return error ? (
      <Alert variant="destructive">
        <AlertTitle>北向配置不可用</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    ) : null
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={view?.mqttLive ? "default" : "outline"}>
          MQTT {view?.mqttLive ? "已接通" : "未接通"}
        </Badge>
        <Badge variant={view?.httpLive ? "default" : "outline"}>
          Webhook {view?.httpLive ? "已启用" : "未启用"}
        </Badge>
        {view?.mqttError ? (
          <span className="text-sm text-destructive">{view.mqttError}</span>
        ) : null}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>北向 MQTT</CardTitle>
          <CardDescription>
            独立 Broker，与南向设备通道分开。主题层级必须用 /。保存后立即热切换，无需重启。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <Field orientation="horizontal">
              <FieldLabel htmlFor="nb-mqtt-enabled">启用 MQTT</FieldLabel>
              <Switch
                id="nb-mqtt-enabled"
                checked={form.mqttEnabled}
                onCheckedChange={(checked) =>
                  setForm((current) => current && { ...current, mqttEnabled: checked })
                }
              />
            </Field>
            <Field>
              <FieldLabel>传输</FieldLabel>
              <Select
                value={form.mqttTransport}
                onValueChange={(value) =>
                  setForm((current) => current && { ...current, mqttTransport: value })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="paho">paho（真实 Broker）</SelectItem>
                  <SelectItem value="memory">memory（进程内）</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            {form.mqttTransport !== "memory" ? (
              <Field>
                <FieldLabel htmlFor="nb-mqtt-url">Broker URI</FieldLabel>
                <Input
                  id="nb-mqtt-url"
                  value={form.mqttUrl}
                  placeholder="tcp://127.0.0.1:1883"
                  onChange={(event) =>
                    setForm((current) => current && { ...current, mqttUrl: event.target.value })
                  }
                />
              </Field>
            ) : null}
            <Field>
              <FieldLabel htmlFor="nb-mqtt-command">入站命令主题</FieldLabel>
              <Input
                id="nb-mqtt-command"
                value={form.mqttCommandTopic}
                placeholder="gw/+/command"
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttCommandTopic: event.target.value }
                  )
                }
              />
              <FieldDescription>
                云端下发命令的订阅过滤。默认 <CodeSample>gw/+/command</CodeSample>
                ，<CodeSample>+</CodeSample> 是单层通配。只用 /，不要写成
                <CodeSample>gw.+.command</CodeSample>。
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-mqtt-response">出站回执主题</FieldLabel>
              <Input
                id="nb-mqtt-response"
                value={form.mqttResponseTopic}
                placeholder="gw/{deviceId}/response"
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttResponseTopic: event.target.value }
                  )
                }
              />
              <FieldDescription>
                指令结果发布模板。用 <CodeSample>{"{deviceId}"}</CodeSample> 占位，运行时换成设备编码，例如
                <CodeSample>gw/F123/response</CodeSample>。
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-mqtt-telemetry">出站遥测主题</FieldLabel>
              <Input
                id="nb-mqtt-telemetry"
                value={form.mqttTelemetryTopic}
                placeholder="gw/{deviceId}/telemetry"
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttTelemetryTopic: event.target.value }
                  )
                }
              />
              <FieldDescription>
                未匹配应答的上报走这里。同样用 <CodeSample>{"{deviceId}"}</CodeSample>，层级用 /。
              </FieldDescription>
            </Field>
            <ConfigExample title="怎么填 · 北向主题">
              <p>
                入站 <CodeSample>gw/+/command</CodeSample>，出站
                <CodeSample>gw/{"{deviceId}"}/response</CodeSample> 与
                <CodeSample>gw/{"{deviceId}"}/telemetry</CodeSample>
                。点号会自动换成 /。
              </p>
            </ConfigExample>
            <Field>
              <FieldLabel htmlFor="nb-mqtt-client">Client ID</FieldLabel>
              <Input
                id="nb-mqtt-client"
                value={form.mqttClientId}
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttClientId: event.target.value }
                  )
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-mqtt-user">用户名</FieldLabel>
              <Input
                id="nb-mqtt-user"
                value={form.mqttUsername}
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttUsername: event.target.value }
                  )
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-mqtt-password">密码</FieldLabel>
              <Input
                id="nb-mqtt-password"
                type="password"
                value={form.mqttPassword}
                placeholder={view?.mqttPasswordSet ? SECRET_MASK : ""}
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, mqttPassword: event.target.value }
                  )
                }
              />
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Webhook</CardTitle>
          <CardDescription>
            与 MQTT 并行扇出。失败只打日志，不回压南向。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <Field orientation="horizontal">
              <FieldLabel htmlFor="nb-http-enabled">启用 Webhook</FieldLabel>
              <Switch
                id="nb-http-enabled"
                checked={form.httpEnabled}
                onCheckedChange={(checked) =>
                  setForm((current) => current && { ...current, httpEnabled: checked })
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-http-url">Webhook URL</FieldLabel>
              <Input
                id="nb-http-url"
                value={form.httpWebhookUrl}
                placeholder="http://127.0.0.1:9090/hook"
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, httpWebhookUrl: event.target.value }
                  )
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-http-timeout">超时（毫秒）</FieldLabel>
              <Input
                id="nb-http-timeout"
                type="number"
                min={1}
                value={form.httpTimeoutMs}
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, httpTimeoutMs: event.target.value }
                  )
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="nb-http-attempts">最多尝试次数</FieldLabel>
              <Input
                id="nb-http-attempts"
                type="number"
                min={1}
                value={form.httpMaxAttempts}
                onChange={(event) =>
                  setForm((current) =>
                    current && { ...current, httpMaxAttempts: event.target.value }
                  )
                }
              />
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button onClick={() => void save()} disabled={saving}>
          {saving ? <Loader2Icon className="size-4 animate-spin" /> : null}
          保存并生效
        </Button>
      </div>
    </div>
  )
}
