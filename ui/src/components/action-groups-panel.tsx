import { Loader2Icon, PlayIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"

import {
  ActionArgumentFields,
  collectActionArguments,
} from "@/components/action-argument-fields"
import { DateTimePicker } from "@/components/date-time-picker"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { catalogApi } from "@/lib/api"
import { toFieldStringMap } from "@/lib/schema-form"
import type {
  ActionGroupView,
  ActionMemberView,
  DeviceEntity,
  FunctionFormView,
  SceneTriggerView,
} from "@/lib/types"

type Kind = "CLUSTER" | "SCENE"

type Props = {
  kind: Kind
}

type MemberDraft = {
  key: string
  deviceCode: string
  functionId: string
  values: Record<string, string>
  optionValue: string
  functions: FunctionFormView[]
}

const EMPTY_TRIGGER: SceneTriggerView = {
  mode: "LISTEN",
  listenMatch: {},
  timerKind: "ONCE",
  timezone: "Asia/Shanghai",
  enabled: true,
}

function nextKey() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function memberFromView(item: ActionMemberView, functions: FunctionFormView[]): MemberDraft {
  const selected = functions.find((fn) => fn.functionId === item.functionId)
  const options = selected?.writeValueOptions ?? []
  const args = item.arguments ?? {}
  const preferred =
    String(args.value ?? "") ||
    options.find((opt) => opt.isDefault)?.mappingValue ||
    options[0]?.mappingValue ||
    options[0]?.optionValue ||
    ""
  return {
    key: item.id || nextKey(),
    deviceCode: item.deviceCode,
    functionId: item.functionId,
    values: toFieldStringMap(args),
    optionValue: preferred,
    functions,
  }
}

export function ActionGroupsPanel({ kind }: Props) {
  const isScene = kind === "SCENE"
  const title = isScene ? "场景" : "集群"
  const [items, setItems] = useState<ActionGroupView[]>([])
  const [devices, setDevices] = useState<DeviceEntity[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<ActionGroupView | null>(null)
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [enabled, setEnabled] = useState(true)
  const [members, setMembers] = useState<MemberDraft[]>([])
  const [trigger, setTrigger] = useState<SceneTriggerView>(EMPTY_TRIGGER)
  const [listenMatchText, setListenMatchText] = useState("")
  const [listenFunctions, setListenFunctions] = useState<FunctionFormView[]>([])
  const [pending, setPending] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [groups, devicePage] = await Promise.all([
        isScene ? catalogApi.listScenes() : catalogApi.listClusters(),
        catalogApi.listDevices(1, 100),
      ])
      setItems(groups)
      setDevices(devicePage.items)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : `加载${title}失败`)
    } finally {
      setLoading(false)
    }
  }, [isScene, title])

  useEffect(() => {
    void load()
  }, [load])

  async function openCreate() {
    setEditing(null)
    setCode("")
    setName("")
    setDescription("")
    setEnabled(true)
    setMembers([])
    setTrigger({ ...EMPTY_TRIGGER })
    setListenMatchText("")
    setListenFunctions([])
    setOpen(true)
  }

  async function openEdit(group: ActionGroupView) {
    setEditing(group)
    setCode(group.code)
    setName(group.name)
    setDescription(group.description ?? "")
    setEnabled(group.enabled)
    const drafts: MemberDraft[] = []
    for (const member of group.members ?? []) {
      let functions: FunctionFormView[] = []
      if (member.deviceCode) {
        try {
          functions = await catalogApi.deviceFunctions(member.deviceCode)
        } catch {
          functions = []
        }
      }
      drafts.push(memberFromView(member, functions))
    }
    setMembers(drafts)
    const nextTrigger = group.trigger ?? { ...EMPTY_TRIGGER }
    setTrigger(nextTrigger)
    setListenMatchText(
      nextTrigger.listenMatch && Object.keys(nextTrigger.listenMatch).length
        ? JSON.stringify(nextTrigger.listenMatch, null, 2)
        : ""
    )
    if (nextTrigger.listenDeviceCode) {
      try {
        setListenFunctions(await catalogApi.deviceFunctions(nextTrigger.listenDeviceCode))
      } catch {
        setListenFunctions([])
      }
    } else {
      setListenFunctions([])
    }
    setOpen(true)
  }

  async function changeMemberDevice(key: string, deviceCode: string) {
    let functions: FunctionFormView[] = []
    if (deviceCode) {
      try {
        functions = await catalogApi.deviceFunctions(deviceCode)
      } catch (error) {
        toast.error(error instanceof Error ? error.message : "加载设备功能失败")
      }
    }
    const first = functions.find((item) => {
      const access = item.accessType?.toUpperCase()
      return access === "WRITE" || access === "READ"
    })
    setMembers((prev) =>
      prev.map((item) =>
        item.key === key
          ? {
              ...item,
              deviceCode,
              functionId: first?.functionId ?? "",
              functions,
              values: toFieldStringMap(first?.values ?? {}),
              optionValue: first?.writeValueOptions?.[0]?.mappingValue
                || first?.writeValueOptions?.[0]?.optionValue
                || "",
            }
          : item
      )
    )
  }

  function changeMemberFunction(key: string, functionId: string) {
    setMembers((prev) =>
      prev.map((item) => {
        if (item.key !== key) {
          return item
        }
        const selected = item.functions.find((fn) => fn.functionId === functionId)
        const options = selected?.writeValueOptions ?? []
        return {
          ...item,
          functionId,
          values: toFieldStringMap(selected?.values ?? {}),
          optionValue:
            options.find((opt) => opt.isDefault)?.mappingValue
            || options[0]?.mappingValue
            || options[0]?.optionValue
            || "",
        }
      })
    )
  }

  function collectMembers() {
    const rows = []
    for (const [index, member] of members.entries()) {
      if (!member.deviceCode || !member.functionId) {
        throw new Error("成员须选择设备与功能")
      }
      const selected = member.functions.find((item) => item.functionId === member.functionId)
      const args = selected
        ? collectActionArguments({
            selected,
            values: member.values,
            selectedOptionValue: member.optionValue,
          })
        : { ok: true as const, args: {} }
      if (!args.ok) {
        throw new Error(args.error)
      }
      rows.push({
        deviceCode: member.deviceCode,
        functionId: member.functionId,
        arguments: args.args,
        sortIndex: index,
      })
    }
    return rows
  }

  function collectTrigger() {
    if (!isScene) {
      return undefined
    }
    let listenMatch: Record<string, unknown> | undefined
    if (listenMatchText.trim()) {
      try {
        listenMatch = JSON.parse(listenMatchText) as Record<string, unknown>
      } catch {
        throw new Error("监听匹配须为 JSON 对象")
      }
    }
    const mode = (trigger.mode || "LISTEN").toUpperCase()
    if (mode === "LISTEN") {
      if (!trigger.listenDeviceCode || !trigger.listenFunctionId) {
        throw new Error("LISTEN 须指定监听设备与功能")
      }
      return {
        mode,
        listenDeviceCode: trigger.listenDeviceCode,
        listenFunctionId: trigger.listenFunctionId,
        listenMatch,
        enabled: trigger.enabled !== false,
      }
    }
    const timerKind = (trigger.timerKind || "ONCE").toUpperCase()
    return {
      mode: "TIMER",
      timerKind,
      timerAt: timerKind === "ONCE" ? trigger.timerAt : undefined,
      cronExpr: timerKind === "CRON" ? trigger.cronExpr : undefined,
      timezone: trigger.timezone || "Asia/Shanghai",
      enabled: trigger.enabled !== false,
    }
  }

  async function save() {
    if (!code.trim() || !name.trim()) {
      toast.error("编码与名称不能为空")
      return
    }
    setPending(true)
    try {
      const body = {
        code: code.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        enabled,
        members: collectMembers(),
        trigger: collectTrigger(),
      }
      if (editing) {
        if (isScene) {
          await catalogApi.updateScene(editing.id, body)
        } else {
          await catalogApi.updateCluster(editing.id, body)
        }
      } else if (isScene) {
        await catalogApi.createScene(body)
      } else {
        await catalogApi.createCluster(body)
      }
      toast.success(`${title}已保存`)
      setOpen(false)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存失败")
    } finally {
      setPending(false)
    }
  }

  async function remove(group: ActionGroupView) {
    if (!window.confirm(`删除${title} ${group.code}？`)) {
      return
    }
    setBusyId(group.id)
    try {
      if (isScene) {
        await catalogApi.deleteScene(group.id)
      } else {
        await catalogApi.deleteCluster(group.id)
      }
      toast.success("已删除")
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除失败")
    } finally {
      setBusyId(null)
    }
  }

  async function execute(group: ActionGroupView) {
    setBusyId(group.id)
    try {
      const result = isScene
        ? await catalogApi.executeScene(group.id)
        : await catalogApi.executeCluster(group.id)
      const failed = (result.items ?? []).filter((item) => item.status !== "SUCCESS")
      if (failed.length === 0) {
        toast.success(`${group.name} 执行完成`)
      } else {
        toast.error(
          `${group.name} 部分失败：${failed
            .map((item) => `${item.deviceId}/${item.functionId} ${item.status}`)
            .join("；")}`
        )
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "执行失败")
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle>{title}</CardTitle>
            <CardDescription>
              {isScene
                ? "同一套成员，由监听 WRITE 成功或日历定时触发。来源 scene 不会再次触发监听。"
                : "手动一键执行多设备功能，跨设备并行，部分失败不阻断。"}
            </CardDescription>
          </div>
          <Button onClick={() => void openCreate()}>
            <PlusIcon />
            新建{title}
          </Button>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2Icon className="size-4 animate-spin" />
              加载中…
            </div>
          ) : items.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>还没有{title}</EmptyTitle>
                <EmptyDescription>先选设备功能作为成员，再保存{title}。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="flex flex-col gap-3">
              {items.map((group) => (
                <div
                  key={group.id}
                  className="flex flex-col gap-2 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">{group.name}</p>
                      <Badge variant="secondary">{group.code}</Badge>
                      {group.enabled ? <Badge>启用</Badge> : <Badge variant="outline">停用</Badge>}
                      {isScene && group.trigger ? (
                        <Badge variant="outline">{group.trigger.mode}</Badge>
                      ) : null}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {(group.members ?? [])
                        .map((member) => `${member.deviceCode}/${member.functionId}`)
                        .join(" · ") || "无成员"}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={busyId === group.id}
                      onClick={() => void execute(group)}
                    >
                      {busyId === group.id ? <Loader2Icon className="animate-spin" /> : <PlayIcon />}
                      执行
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => void openEdit(group)}>
                      编辑
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={busyId === group.id}
                      onClick={() => void remove(group)}
                    >
                      <Trash2Icon />
                      删除
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editing ? `编辑${title}` : `新建${title}`}</DialogTitle>
            <DialogDescription>成员默认 arguments 与手动下发表单相同。</DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>编码 *</FieldLabel>
              <Input value={code} onChange={(event) => setCode(event.target.value)} disabled={!!editing} />
            </Field>
            <Field>
              <FieldLabel>名称 *</FieldLabel>
              <Input value={name} onChange={(event) => setName(event.target.value)} />
            </Field>
            <Field>
              <FieldLabel>说明</FieldLabel>
              <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
            </Field>
            <Field className="flex flex-row items-center justify-between rounded-lg border p-3">
              <FieldLabel>启用</FieldLabel>
              <Switch checked={enabled} onCheckedChange={setEnabled} />
            </Field>

            {isScene ? (
              <>
                <Field>
                  <FieldLabel>触发方式</FieldLabel>
                  <Select
                    value={trigger.mode || "LISTEN"}
                    onValueChange={(mode) => setTrigger((prev) => ({ ...prev, mode }))}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="LISTEN">监听 WRITE 成功</SelectItem>
                      <SelectItem value="TIMER">日历定时</SelectItem>
                    </SelectContent>
                  </Select>
                </Field>
                {(trigger.mode || "LISTEN") === "LISTEN" ? (
                  <>
                    <Field>
                      <FieldLabel>监听设备 *</FieldLabel>
                      <Select
                        value={trigger.listenDeviceCode || ""}
                        onValueChange={(listenDeviceCode) => {
                          setTrigger((prev) => ({ ...prev, listenDeviceCode, listenFunctionId: "" }))
                          if (!listenDeviceCode) {
                            setListenFunctions([])
                            return
                          }
                          void catalogApi.deviceFunctions(listenDeviceCode)
                            .then(setListenFunctions)
                            .catch((error) => {
                              setListenFunctions([])
                              toast.error(error instanceof Error ? error.message : "加载监听功能失败")
                            })
                        }}
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择设备" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {devices.map((device) => (
                              <SelectItem key={device.deviceCode} value={device.deviceCode}>
                                {device.name || device.deviceCode}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    </Field>
                    <Field>
                      <FieldLabel>监听功能 *</FieldLabel>
                      <Select
                        value={trigger.listenFunctionId || ""}
                        onValueChange={(listenFunctionId) =>
                          setTrigger((prev) => ({ ...prev, listenFunctionId }))
                        }
                        disabled={!trigger.listenDeviceCode}
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择 WRITE 功能" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {listenFunctions
                              .filter((item) => item.accessType?.toUpperCase() === "WRITE")
                              .map((item) => (
                                <SelectItem key={item.functionId} value={item.functionId}>
                                  {item.description && item.description !== item.functionId
                                    ? `${item.functionId} · ${item.description}`
                                    : item.functionId}
                                </SelectItem>
                              ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    </Field>
                    <Field>
                      <FieldLabel>参数子集匹配（JSON，可选）</FieldLabel>
                      <Textarea
                        value={listenMatchText}
                        onChange={(event) => setListenMatchText(event.target.value)}
                        placeholder='{"value":"open"}'
                      />
                    </Field>
                  </>
                ) : (
                  <>
                    <Field>
                      <FieldLabel>定时类型</FieldLabel>
                      <Select
                        value={trigger.timerKind || "ONCE"}
                        onValueChange={(timerKind) => setTrigger((prev) => ({ ...prev, timerKind }))}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ONCE">单次</SelectItem>
                          <SelectItem value="CRON">Cron</SelectItem>
                        </SelectContent>
                      </Select>
                    </Field>
                    {(trigger.timerKind || "ONCE") === "ONCE" ? (
                      <Field>
                        <FieldLabel>执行时间 *</FieldLabel>
                        <DateTimePicker
                          id="scene-timer-at"
                          value={trigger.timerAt ?? ""}
                          onChange={(timerAt) => setTrigger((prev) => ({ ...prev, timerAt }))}
                        />
                      </Field>
                    ) : (
                      <Field>
                        <FieldLabel>Cron（6 位）*</FieldLabel>
                        <Input
                          value={trigger.cronExpr || ""}
                          onChange={(event) =>
                            setTrigger((prev) => ({ ...prev, cronExpr: event.target.value }))
                          }
                          placeholder="0 0 8 * * *"
                        />
                      </Field>
                    )}
                    <Field>
                      <FieldLabel>时区</FieldLabel>
                      <Input
                        value={trigger.timezone || "Asia/Shanghai"}
                        onChange={(event) =>
                          setTrigger((prev) => ({ ...prev, timezone: event.target.value }))
                        }
                      />
                    </Field>
                  </>
                )}
              </>
            ) : null}

            <div className="flex items-center justify-between">
              <p className="text-sm font-medium">成员</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  setMembers((prev) => [
                    ...prev,
                    {
                      key: nextKey(),
                      deviceCode: "",
                      functionId: "",
                      values: {},
                      optionValue: "",
                      functions: [],
                    },
                  ])
                }
              >
                <PlusIcon />
                添加成员
              </Button>
            </div>
            {members.map((member) => {
              const selected =
                member.functions.find((item) => item.functionId === member.functionId) ?? null
              const commandable = member.functions.filter((item) => {
                const access = item.accessType?.toUpperCase()
                return access === "WRITE" || access === "READ"
              })
              return (
                <div key={member.key} className="space-y-3 rounded-lg border p-3">
                  <div className="flex justify-end">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setMembers((prev) => prev.filter((item) => item.key !== member.key))}
                    >
                      <Trash2Icon />
                      移除
                    </Button>
                  </div>
                  <Field>
                    <FieldLabel>设备</FieldLabel>
                    <Select
                      value={member.deviceCode}
                      onValueChange={(value) => void changeMemberDevice(member.key, value)}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="选择设备" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {devices.map((device) => (
                            <SelectItem key={device.deviceCode} value={device.deviceCode}>
                              {device.name || device.deviceCode}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel>功能</FieldLabel>
                    <Select
                      value={member.functionId}
                      onValueChange={(value) => changeMemberFunction(member.key, value)}
                      disabled={!member.deviceCode}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="选择功能" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {commandable.map((item) => (
                            <SelectItem key={item.functionId} value={item.functionId}>
                              {item.description && item.description !== item.functionId
                                ? `${item.functionId} · ${item.description}`
                                : item.functionId}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                  <ActionArgumentFields
                    selected={selected}
                    values={member.values}
                    onValuesChange={(values) =>
                      setMembers((prev) =>
                        prev.map((item) => (item.key === member.key ? { ...item, values } : item))
                      )
                    }
                    selectedOptionValue={member.optionValue}
                    onSelectedOptionValue={(optionValue) =>
                      setMembers((prev) =>
                        prev.map((item) =>
                          item.key === member.key ? { ...item, optionValue } : item
                        )
                      )
                    }
                    pending={pending}
                    idPrefix={`member-${member.key}`}
                  />
                </div>
              )
            })}
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={pending}>
              取消
            </Button>
            <Button onClick={() => void save()} disabled={pending}>
              {pending ? <Loader2Icon data-icon="inline-start" className="animate-spin" /> : null}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
