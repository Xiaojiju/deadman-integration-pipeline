import { Loader2Icon, PlusIcon, RefreshCwIcon } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"

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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { catalogApi } from "@/lib/api"
import type { ProductTypeView } from "@/lib/types"

type Props = {
  onChanged?: () => void
}

export function ProductTypesPanel({ onChanged }: Props) {
  const [types, setTypes] = useState<ProductTypeView[]>([])
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<ProductTypeView | null>(null)
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setTypes(await catalogApi.listProductTypes())
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载产品类型失败")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  function openCreate() {
    setEditing(null)
    setCode("")
    setName("")
    setDescription("")
    setDialogOpen(true)
  }

  function openEdit(type: ProductTypeView) {
    setEditing(type)
    setCode(type.code)
    setName(type.name)
    setDescription(type.description ?? "")
    setDialogOpen(true)
  }

  async function save() {
    if (!editing && (!code.trim() || !name.trim())) {
      toast.error("请填写类型编码与名称")
      return
    }
    if (editing && !name.trim()) {
      toast.error("请填写类型名称")
      return
    }
    setPending(true)
    try {
      if (editing) {
        await catalogApi.updateProductType(editing.id, {
          name: name.trim(),
          description: description.trim(),
        })
        toast.success(`类型 ${editing.code} 已更新`)
      } else {
        await catalogApi.createProductType({
          code: code.trim(),
          name: name.trim(),
          description: description.trim() || undefined,
        })
        toast.success(`类型 ${code} 已创建`)
      }
      setDialogOpen(false)
      await load()
      onChanged?.()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存产品类型失败")
    } finally {
      setPending(false)
    }
  }

  async function remove(type: ProductTypeView) {
    if (!window.confirm(`确认删除产品类型 ${type.code}？仍被产品引用时无法删除。`)) {
      return
    }
    setPending(true)
    try {
      await catalogApi.deleteProductType(type.id)
      toast.success(`类型 ${type.code} 已删除`)
      await load()
      onChanged?.()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除产品类型失败")
    } finally {
      setPending(false)
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>产品类型</CardTitle>
          <CardDescription>
            与南向能力类型分开。创建产品时必选；扫描门禁设备要求类型为 ACCESS_CONTROL。
          </CardDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            <RefreshCwIcon data-icon="inline-start" />
            刷新
          </Button>
          <Button size="sm" onClick={openCreate}>
            <PlusIcon data-icon="inline-start" />
            新建类型
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载中…
          </div>
        ) : types.length === 0 ? (
          <Empty className="border border-dashed">
            <EmptyHeader>
              <EmptyTitle>暂无产品类型</EmptyTitle>
              <EmptyDescription>请确认已执行 V17 SQL，或在此新建类型。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>编码</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>说明</TableHead>
                <TableHead className="w-40">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {types.map((type) => (
                <TableRow key={type.id}>
                  <TableCell className="font-mono text-sm">
                    {type.code}
                    {type.code === "ACCESS_CONTROL" ? (
                      <Badge variant="outline" className="ml-2">
                        门禁
                      </Badge>
                    ) : null}
                  </TableCell>
                  <TableCell>{type.name}</TableCell>
                  <TableCell className="text-muted-foreground">{type.description || "—"}</TableCell>
                  <TableCell className="flex gap-1">
                    <Button variant="ghost" size="sm" disabled={pending} onClick={() => openEdit(type)}>
                      编辑
                    </Button>
                    <Button variant="ghost" size="sm" disabled={pending} onClick={() => void remove(type)}>
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "编辑产品类型" : "新建产品类型"}</DialogTitle>
            <DialogDescription>
              {editing ? "可修改名称与说明；编码不可改。" : "编码创建后不可改。"}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="typeCode">编码</FieldLabel>
              <Input
                id="typeCode"
                value={code}
                disabled={Boolean(editing)}
                onChange={(event) => setCode(event.target.value)}
                placeholder="ACCESS_CONTROL"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="typeName">名称</FieldLabel>
              <Input
                id="typeName"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="门禁"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="typeDesc">说明</FieldLabel>
              <Input
                id="typeDesc"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={pending}>
              取消
            </Button>
            <Button onClick={() => void save()} disabled={pending}>
              {editing ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
