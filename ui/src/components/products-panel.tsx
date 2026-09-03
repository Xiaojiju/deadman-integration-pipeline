import { Loader2Icon, PlusIcon, RefreshCwIcon } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import {
  FixedPropertyDescriptionEditor,
  mergeFixedProperties,
  schemaToFixedProperties,
} from "@/components/fixed-property-description-editor"
import {
  FixedValueOptionPicker,
  mergeFixedOptions,
} from "@/components/fixed-value-option-picker"
import {
  FixedWriteFieldEditor,
  mergeFixedWriteFields,
  schemaToFixedWriteFields,
} from "@/components/fixed-write-field-editor"
import { filterValidValueOptions } from "@/components/value-option-list-editor"
import {
  ContractFieldEditor,
  mergeContractFields,
  schemaToContractFields,
} from "@/components/contract-field-editor"
import { FieldNodeTreeEditor } from "@/components/field-node-tree-editor"
import { ValueMappingEditor, filterValidMappings } from "@/components/value-mapping-editor"
import { ListPagination } from "@/components/list-pagination"
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
import { Switch } from "@/components/ui/switch"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ConfigExample, CodeSample } from "@/components/config-example"
import { catalogApi, MIN_SCHEDULE_MS } from "@/lib/api"
import type {
  CapabilityDescriptor,
  ProductEntity,
  ProductFunctionEntity,
  PropertyItem,
  SchemaField,
  ValueOption,
  WriteFieldOption,
} from "@/lib/types"
import type { FieldNodeModel, ValueMappingModel } from "@/lib/payload-form"
import {
  emptyObjectRoot,
  fieldNodeToWriteFields,
  fieldOptionsToMappings,
  mergeMappingsIntoFields,
  mappingsToValueOptions,
  resolvePayloadMode,
  valueOptionsToMappings,
  writeFieldsToFieldNode,
} from "@/lib/payload-form"
import {
  WriteFieldListEditor,
  filterValidWriteFields,
} from "@/components/write-field-list-editor"

const PAGE_SIZE = 20

type Props = {
  productOptions: ProductEntity[]
  capabilities: CapabilityDescriptor[]
  onChanged: () => void
}

export function ProductsPanel({ productOptions, capabilities, onChanged }: Props) {
  const [products, setProducts] = useState<ProductEntity[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)

  const [productDialogOpen, setProductDialogOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<ProductEntity | null>(null)
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [seedCapabilityType, setSeedCapabilityType] = useState("")

  const [viewProduct, setViewProduct] = useState<ProductEntity | null>(null)
  const [productFunctions, setProductFunctions] = useState<ProductFunctionEntity[]>([])
  const [functionsLoading, setFunctionsLoading] = useState(false)

  const [fnOpen, setFnOpen] = useState(false)
  const [fnMode, setFnMode] = useState<"create" | "edit">("create")
  const [editingFunction, setEditingFunction] = useState<ProductFunctionEntity | null>(null)
  const [productId, setProductId] = useState("")
  const [capabilityType, setCapabilityType] = useState("")
  const [functionId, setFunctionId] = useState("")
  const [accessType, setAccessType] = useState("WRITE")
  const [sortIndex, setSortIndex] = useState("0")
  const [customProperties, setCustomProperties] = useState<PropertyItem[]>([])
  const [writeValueOptions, setWriteValueOptions] = useState<ValueOption[]>([])
  const [writeFields, setWriteFields] = useState<WriteFieldOption[]>([])
  const [readFields, setReadFields] = useState<WriteFieldOption[]>([])
  const [readValueOptions, setReadValueOptions] = useState<ValueOption[]>([])
  const [fnDescription, setFnDescription] = useState("")
  const [publishTopicSlot, setPublishTopicSlot] = useState("")
  const [subscribeTopicSlot, setSubscribeTopicSlot] = useState("")
  const [replyTopicSlot, setReplyTopicSlot] = useState("")
  const [correlationPath, setCorrelationPath] = useState("")
  const [resultPath, setResultPath] = useState("")
  const [replyTimeoutMs, setReplyTimeoutMs] = useState("")
  const [scheduleEnabled, setScheduleEnabled] = useState(false)
  const [scheduleIntervalMs, setScheduleIntervalMs] = useState("")
  const [payloadMode, setPayloadMode] = useState<"VALUE" | "STRUCT">("STRUCT")
  const [payloadEncoding, setPayloadEncoding] = useState<"JSON" | "HEX" | "BINARY">("JSON")
  const [structRoot, setStructRoot] = useState<FieldNodeModel>(() => emptyObjectRoot())
  const [valueMappings, setValueMappings] = useState<ValueMappingModel[]>([])

  const selectedCapability = capabilities.find((item) => item.capabilityType === capabilityType)
  const templates = selectedCapability?.functionTemplates ?? []
  const isFixed = selectedCapability?.functionMode === "FIXED"
  const isContracted = selectedCapability?.functionMode === "CONTRACT"
  const selectedTemplate = templates.find((item) => item.functionId === functionId)
  /** OPEN 能力下无参预置功能（如 MQTT publish/subscribe）结构锁定 */
  const isCapabilityDefaultFn = Boolean(
    !isFixed &&
      !isContracted &&
      selectedTemplate &&
      (selectedTemplate.parameters?.length ?? 0) === 0
  )
  const structureLocked = isFixed || isCapabilityDefaultFn
  const contractTemplate = templates.find((item) => item.accessType === accessType)
  const contractSchema: SchemaField[] = useMemo(
    () => contractTemplate?.parameters ?? [],
    [contractTemplate]
  )
  const paramSchema: SchemaField[] = useMemo(
    () => (isContracted ? contractSchema : selectedTemplate?.parameters ?? []),
    [isContracted, contractSchema, selectedTemplate]
  )
  const templateFixedProperties = useMemo(
    () => schemaToFixedProperties(paramSchema),
    [paramSchema]
  )
  const templateFixedWriteFields = useMemo(
    () => schemaToFixedWriteFields(paramSchema),
    [paramSchema]
  )
  const displayFixedProperties = useMemo(
    () => mergeFixedProperties(templateFixedProperties, customProperties),
    [templateFixedProperties, customProperties]
  )
  const displayFixedWriteFields = useMemo(
    () => mergeFixedWriteFields(templateFixedWriteFields, writeFields, writeValueOptions),
    [templateFixedWriteFields, writeFields, writeValueOptions]
  )
  const displayFixedReadOptions = useMemo(
    () => mergeFixedOptions(
      templateFixedWriteFields.flatMap((item) => item.options ?? []),
      readValueOptions
    ).filter((item) =>
      readValueOptions.some((saved) => saved.optionValue === item.optionValue)
    ),
    [templateFixedWriteFields, readValueOptions]
  )

  const load = useCallback(async (targetPage = page) => {
    setLoading(true)
    try {
      const result = await catalogApi.listProducts(targetPage, PAGE_SIZE)
      setProducts(result.items)
      setPage(result.page)
      setTotal(result.total)
      setTotalPages(result.totalPages)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载产品失败")
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load(page)
  }, [page]) // eslint-disable-line react-hooks/exhaustive-deps

  async function reloadFunctions(product: ProductEntity) {
    const list = await catalogApi.listProductFunctions(product.id)
    setProductFunctions(list)
  }

  function openCreateProduct() {
    setEditingProduct(null)
    setCode("")
    setName("")
    setDescription("")
    setSeedCapabilityType("")
    setProductDialogOpen(true)
  }

  function openEditProduct(product: ProductEntity) {
    setEditingProduct(product)
    setCode(product.code)
    setName(product.name)
    setDescription(product.description ?? "")
    setSeedCapabilityType("")
    setProductDialogOpen(true)
  }

  async function saveProduct() {
    if (!editingProduct && (!code.trim() || !name.trim())) {
      toast.error("请填写产品编码与名称")
      return
    }
    if (editingProduct && !name.trim()) {
      toast.error("请填写产品名称")
      return
    }
    setPending(true)
    try {
      if (editingProduct) {
        await catalogApi.updateProduct(editingProduct.id, {
          name: name.trim(),
          description: description.trim(),
        })
        toast.success(`产品 ${editingProduct.code} 已更新`)
      } else {
        await catalogApi.createProduct({
          code: code.trim(),
          name: name.trim(),
          description: description.trim() || undefined,
          seedCapabilityType: seedCapabilityType || undefined,
        })
        toast.success(`产品 ${code} 已创建`)
        setPage(1)
        await load(1)
      }
      setProductDialogOpen(false)
      if (editingProduct) {
        await load(page)
      }
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存产品失败")
    } finally {
      setPending(false)
    }
  }

  async function removeProduct(product: ProductEntity) {
    if (!window.confirm(`确认删除产品 ${product.code}？`)) {
      return
    }
    setPending(true)
    try {
      await catalogApi.deleteProduct(product.id)
      toast.success(`产品 ${product.code} 已删除`)
      await load(page)
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除产品失败")
    } finally {
      setPending(false)
    }
  }

  async function viewFunctions(product: ProductEntity) {
    setViewProduct(product)
    setFunctionsLoading(true)
    setProductFunctions([])
    try {
      await reloadFunctions(product)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载产品功能失败")
      setViewProduct(null)
    } finally {
      setFunctionsLoading(false)
    }
  }

  function seedContractedFields(cap: CapabilityDescriptor | undefined, access: string) {
    const tpl = cap?.functionTemplates.find((item) => item.accessType === access)
    return schemaToContractFields(tpl?.parameters ?? [])
  }

  function openCreateFunction(forProduct?: ProductEntity) {
    const target = forProduct ?? viewProduct
    const firstCap = capabilities[0]
    const contracted = firstCap?.functionMode === "CONTRACT"
    setFnMode("create")
    setEditingFunction(null)
    setProductId(target?.id ?? productOptions[0]?.id ?? "")
    setCapabilityType(firstCap?.capabilityType ?? "")
    setFunctionId("")
    setAccessType("WRITE")
    setSortIndex("0")
    setCustomProperties([])
    setWriteValueOptions([])
    const seeded = contracted ? seedContractedFields(firstCap, "WRITE") : []
    setWriteFields(seeded)
    setReadFields([])
    setReadValueOptions([])
    setFnDescription("")
    setPublishTopicSlot("")
    setSubscribeTopicSlot("")
    setReplyTopicSlot("")
    setCorrelationPath("")
    setResultPath("")
    setReplyTimeoutMs("")
    setScheduleEnabled(false)
    setScheduleIntervalMs("")
    setPayloadMode(contracted ? "VALUE" : "STRUCT")
    setPayloadEncoding("JSON")
    setStructRoot(emptyObjectRoot())
    setValueMappings([])
    setFnOpen(true)
  }

  function openEditFunction(fn: ProductFunctionEntity) {
    setFnMode("edit")
    setEditingFunction(fn)
    setProductId(fn.productId)
    setCapabilityType(fn.capabilityType ?? "")
    setFunctionId(fn.functionId)
    setAccessType(fn.accessType || "WRITE")
    setSortIndex(String(fn.sortIndex ?? 0))
    setFnDescription(fn.description ?? "")
    setCustomProperties(fn.properties ?? [])
    setWriteValueOptions(fn.writeValueOptions ?? [])
    setWriteFields(fn.writeFields ?? [])
    setReadFields(fn.readFields ?? [])
    setReadValueOptions(fn.readValueOptions ?? [])
    setPublishTopicSlot(fn.publishTopicSlot ?? "")
    setSubscribeTopicSlot(fn.subscribeTopicSlot ?? "")
    setReplyTopicSlot(fn.replyTopicSlot ?? "")
    setCorrelationPath(fn.correlationPath ?? "")
    setResultPath(fn.resultPath ?? "")
    setReplyTimeoutMs(fn.replyTimeoutMs != null ? String(fn.replyTimeoutMs) : "")
    setScheduleEnabled(fn.scheduleEnabled === true)
    setScheduleIntervalMs(fn.scheduleIntervalMs != null ? String(fn.scheduleIntervalMs) : "")
    const mode = resolvePayloadMode(fn)
    setPayloadMode(mode)
    const encoding = (fn.payloadEncoding || "JSON").toUpperCase()
    setPayloadEncoding(encoding === "HEX" || encoding === "BINARY" ? encoding : "JSON")
    setStructRoot(writeFieldsToFieldNode(fn.writeFields ?? []))
    const fromFields = fieldOptionsToMappings(fn.writeFields ?? [])
    setValueMappings(
      fromFields.length > 0
        ? fromFields
        : valueOptionsToMappings(fn.writeValueOptions ?? [], fn.writeFields ?? [])
    )
    setFnOpen(true)
  }

  function seedOptionsFromTemplate(template?: { parameters?: SchemaField[]; accessType?: string; description?: string }) {
    if (!template) {
      return
    }
    if (template.accessType) {
      setAccessType(template.accessType)
    }
    if (template.description) {
      setFnDescription(template.description)
    }
    const props = schemaToFixedProperties(template.parameters ?? [])
    const fields = schemaToFixedWriteFields(template.parameters ?? [])
    setCustomProperties(props)
    setWriteFields(fields)
    setStructRoot(writeFieldsToFieldNode(fields))
    setWriteValueOptions([])
    setReadValueOptions([])
    setPayloadMode("STRUCT")
    setPayloadEncoding("JSON")
    setValueMappings([])
  }

  function onCapabilityOrFunctionChange(nextCapability: string, nextFunctionId: string) {
    setCapabilityType(nextCapability)
    setFunctionId(nextFunctionId)
    const cap = capabilities.find((item) => item.capabilityType === nextCapability)
    if (cap?.functionMode === "CONTRACT") {
      if (fnMode === "create") {
        const access = accessType || "WRITE"
        const seeded = seedContractedFields(cap, access)
        if (access === "READ") {
          setReadFields(seeded)
          setWriteFields([])
        } else {
          setWriteFields(seeded)
          setReadFields([])
        }
        setPayloadMode("VALUE")
        setPayloadEncoding("JSON")
        setStructRoot(emptyObjectRoot())
        setValueMappings([])
      }
      return
    }
    const template = cap?.functionTemplates.find((item) => item.functionId === nextFunctionId)
    if (fnMode === "create") {
      if (template) {
        seedOptionsFromTemplate(template)
      } else {
        setWriteFields([])
        setReadFields([])
        setStructRoot(emptyObjectRoot())
        setPayloadMode("STRUCT")
        setPayloadEncoding("JSON")
        setValueMappings([])
        setWriteValueOptions([])
        setReadValueOptions([])
        setCustomProperties([])
      }
    }
  }

  async function saveFunction() {
    if (!productId || !capabilityType || !functionId.trim()) {
      toast.error("请选择产品、能力与功能")
      return
    }
    if (isFixed && !selectedTemplate) {
      toast.error("FIXED 功能必须选择能力预置模板")
      return
    }
    const lockedOpenDefault = isCapabilityDefaultFn
    const properties = isFixed ? displayFixedProperties : undefined
    const fixedFields = isFixed ? displayFixedWriteFields : []
    const mappings = !isFixed && !lockedOpenDefault && payloadMode === "VALUE"
      ? filterValidMappings(valueMappings).map((row) =>
          isContracted
            ? {
                ...row,
                patches: (row.patches ?? []).map((patch) => ({
                  ...patch,
                  path: patch.path?.trim() || "value",
                })),
              }
            : row
        )
      : []
    const contractBound = isContracted
      ? mergeContractFields(
          schemaToContractFields(contractSchema),
          accessType === "READ" ? readFields : writeFields
        )
      : []
    const contractWithMaps = isContracted && payloadMode === "VALUE"
      ? mergeMappingsIntoFields(contractBound, mappings)
      : contractBound
    const writeStructFields = isFixed
      ? fixedFields
      : lockedOpenDefault
        ? []
        : accessType === "WRITE"
          ? (isContracted ? contractWithMaps : mergeMappingsIntoFields(fieldNodeToWriteFields(structRoot), mappings))
          : []
    const writeOpts = isFixed || lockedOpenDefault
      ? []
      : mappingsToValueOptions(mappings)
    const readStructFields = isFixed || lockedOpenDefault
      ? []
      : accessType === "READ"
        ? (isContracted ? filterValidWriteFields(contractBound) : filterValidWriteFields(readFields))
        : []
    const readOpts = isFixed && !lockedOpenDefault ? filterValidValueOptions(readValueOptions) : []

    const intervalValue = scheduleIntervalMs.trim() === "" ? 0 : Number(scheduleIntervalMs)
    if (scheduleIntervalMs.trim() && (!Number.isFinite(intervalValue) || intervalValue < MIN_SCHEDULE_MS)) {
      toast.error(`定时间隔不能小于 ${MIN_SCHEDULE_MS} 毫秒`)
      return
    }
    if (scheduleEnabled && intervalValue < MIN_SCHEDULE_MS) {
      toast.error(`启用定时下发时须填写不少于 ${MIN_SCHEDULE_MS} 毫秒的间隔`)
      return
    }
    const timeoutValue = replyTimeoutMs.trim() === "" ? 0 : Number(replyTimeoutMs)
    if (capabilityType === "MQTT" && replyTimeoutMs.trim() && (!Number.isFinite(timeoutValue) || timeoutValue < 0)) {
      toast.error("应答超时须为非负整数毫秒")
      return
    }

    setPending(true)
    try {
      const body = {
        accessType: lockedOpenDefault ? selectedTemplate?.accessType ?? accessType : accessType,
        properties: lockedOpenDefault ? undefined : properties,
        writeAccessType: payloadMode === "VALUE" ? "VALUE" : "STRUCT",
        writeValueOptions: writeOpts,
        writeFields: writeStructFields,
        readFields: readStructFields,
        readValueOptions: readOpts,
        sortIndex: Number(sortIndex) || 0,
        description: fnDescription.trim() || undefined,
        publishTopicSlot: capabilityType === "MQTT" ? publishTopicSlot.trim() || undefined : undefined,
        subscribeTopicSlot: capabilityType === "MQTT" ? subscribeTopicSlot.trim() || undefined : undefined,
        payloadMode: isFixed || lockedOpenDefault ? undefined : payloadMode,
        payloadEncoding: isFixed || lockedOpenDefault || isContracted ? undefined : payloadEncoding,
        replyTopicSlot: capabilityType === "MQTT" ? replyTopicSlot.trim() : undefined,
        correlationPath: capabilityType === "MQTT" ? correlationPath.trim() : undefined,
        resultPath: capabilityType === "MQTT" ? resultPath.trim() : undefined,
        replyTimeoutMs: capabilityType === "MQTT" ? timeoutValue : undefined,
        scheduleEnabled,
        scheduleIntervalMs: intervalValue,
      }
      if (fnMode === "edit" && editingFunction) {
        await catalogApi.updateProductFunction(productId, editingFunction.functionId, body)
        toast.success(`功能 ${editingFunction.functionId} 已更新`)
      } else {
        await catalogApi.createProductFunction(productId, {
          functionId: functionId.trim(),
          capabilityType,
          ...body,
          accessType: isFixed ? undefined : accessType,
        })
        toast.success(`已创建功能 ${functionId}`)
      }
      setFnOpen(false)
      if (viewProduct && viewProduct.id === productId) {
        await reloadFunctions(viewProduct)
      }
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存功能失败")
    } finally {
      setPending(false)
    }
  }

  async function importAll() {
    if (!productId || !capabilityType) {
      toast.error("请选择产品与能力")
      return
    }
    setPending(true)
    try {
      await catalogApi.importProductFunctions(productId, capabilityType)
      toast.success("已按能力模板批量挂载功能")
      setFnOpen(false)
      if (viewProduct && viewProduct.id === productId) {
        await reloadFunctions(viewProduct)
      }
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "批量挂载失败")
    } finally {
      setPending(false)
    }
  }

  async function removeFunction(targetFunctionId: string) {
    if (!viewProduct) {
      return
    }
    if (!window.confirm(`确认从产品移除功能 ${targetFunctionId}？`)) {
      return
    }
    setPending(true)
    try {
      await catalogApi.deleteProductFunction(viewProduct.id, targetFunctionId)
      toast.success(`已移除 ${targetFunctionId}`)
      await reloadFunctions(viewProduct)
      onChanged()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "移除功能失败")
    } finally {
      setPending(false)
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <CardTitle>产品</CardTitle>
          <CardDescription>
            同质设备模板。FIXED 一键导入封闭 API；CONTRACT（如 Modbus）自定义业务 functionId、参数名锁死；OPEN 可自定义协议字段。
          </CardDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load(page)} disabled={loading}>
            <RefreshCwIcon data-icon="inline-start" />
            刷新
          </Button>
          <Button size="sm" variant="outline" onClick={() => openCreateFunction()}>
            创建功能
          </Button>
          <Button size="sm" onClick={openCreateProduct}>
            <PlusIcon data-icon="inline-start" />
            新建产品
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="size-4 animate-spin" />
            加载中…
          </div>
        ) : products.length === 0 ? (
          <Empty className="border border-dashed">
            <EmptyHeader>
              <EmptyTitle>暂无产品</EmptyTitle>
              <EmptyDescription>创建产品后，再为其创建或导入功能。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>编码</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>说明</TableHead>
                <TableHead className="w-52">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {products.map((product) => (
                <TableRow key={product.id}>
                  <TableCell className="font-mono text-sm">{product.code}</TableCell>
                  <TableCell>{product.name}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {product.description || "—"}
                  </TableCell>
                  <TableCell className="flex flex-wrap gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={pending}
                      onClick={() => void viewFunctions(product)}
                    >
                      功能
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={pending}
                      onClick={() => openEditProduct(product)}
                    >
                      编辑
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={pending}
                      onClick={() => void removeProduct(product)}
                    >
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <ListPagination
          page={page}
          totalPages={totalPages}
          total={total}
          onPageChange={setPage}
        />
      </CardContent>

      <Dialog open={productDialogOpen} onOpenChange={setProductDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingProduct ? "编辑产品" : "新建产品"}</DialogTitle>
            <DialogDescription>
              {editingProduct ? "可修改名称与说明；编码不可改。" : "定义同质设备的共享功能模板。"}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="productCode">编码</FieldLabel>
              <Input
                id="productCode"
                value={code}
                disabled={Boolean(editingProduct)}
                onChange={(e) => setCode(e.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="productName">名称</FieldLabel>
              <Input id="productName" value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="productDesc">说明</FieldLabel>
              <Input
                id="productDesc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </Field>
            {!editingProduct ? (
              <Field>
                <FieldLabel>创建后导入能力功能（可选）</FieldLabel>
                <Select
                  value={seedCapabilityType || "__none__"}
                  onValueChange={(value) =>
                    setSeedCapabilityType(value === "__none__" ? "" : value)
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="不导入" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="__none__">不导入</SelectItem>
                      {capabilities.map((item) => (
                        <SelectItem key={item.capabilityType} value={item.capabilityType}>
                          {item.capabilityType}
                          {item.functionMode === "FIXED"
                            ? " · FIXED"
                            : item.functionMode === "CONTRACT"
                              ? " · CONTRACT"
                              : " · OPEN"}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setProductDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={() => void saveProduct()} disabled={pending}>
              {editingProduct ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={fnOpen} onOpenChange={setFnOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{fnMode === "edit" ? "编辑产品功能" : "创建产品功能"}</DialogTitle>
            <DialogDescription>
              {fnMode === "edit"
                ? structureLocked
                  ? "预置功能结构不可改，仅可维护说明与排序。"
                  : isContracted
                    ? "CONTRACT：functionId 自定义，area/offset 等参数名锁死，只配来源与取值。"
                    : "FIXED：默认参数与写字段的 field/取值不可改，可改说明；OPEN：读/写均可配置字段类型与约束。"
                : isContracted
                  ? "自定义业务 functionId（如 light.switch）。参数名由能力契约锁死。"
                  : "FIXED 从预置模板选功能。OPEN（如 MQTT）自定义 functionId，并配置协议字段与调用方式。"}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>产品</FieldLabel>
              <Select
                value={productId}
                disabled={fnMode === "edit"}
                onValueChange={setProductId}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择产品" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {productOptions.map((product) => (
                      <SelectItem key={product.id} value={product.id}>
                        {product.name || product.code}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>能力</FieldLabel>
              <Select
                value={capabilityType}
                disabled={fnMode === "edit"}
                onValueChange={(value) => onCapabilityOrFunctionChange(value, "")}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择能力" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {capabilities.map((item) => (
                      <SelectItem key={item.capabilityType} value={item.capabilityType}>
                        {item.capabilityType}
                        {item.functionMode === "FIXED"
                          ? " · FIXED"
                          : item.functionMode === "CONTRACT"
                            ? " · CONTRACT"
                            : " · OPEN"}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {fnMode === "create" && (isFixed || (templates.length > 0 && !isContracted)) ? (
              <Field>
                <FieldLabel>功能（模板）</FieldLabel>
                <Select
                  value={functionId}
                  onValueChange={(value) => onCapabilityOrFunctionChange(capabilityType, value)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择功能" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {templates.map((item) => (
                        <SelectItem key={item.functionId} value={item.functionId}>
                          {item.functionId}
                          {item.description ? ` · ${item.description}` : ""}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
            {fnMode === "create" && !isFixed ? (
              <Field>
                <FieldLabel htmlFor="customFn">自定义 functionId</FieldLabel>
                <Input
                  id="customFn"
                  value={functionId}
                  onChange={(e) => setFunctionId(e.target.value)}
                  placeholder={isContracted ? "例如 light.switch" : "例如 pump.readTemp"}
                />
              </Field>
            ) : null}
            {fnMode === "edit" ? (
              <Field>
                <FieldLabel>functionId</FieldLabel>
                <Input value={functionId} disabled />
              </Field>
            ) : null}
            <Field>
              <FieldLabel htmlFor="fnDescription">功能说明</FieldLabel>
              <Input
                id="fnDescription"
                value={fnDescription}
                onChange={(e) => setFnDescription(e.target.value)}
                placeholder="如：远程控门"
              />
            </Field>
            <Field>
              <FieldLabel>accessType</FieldLabel>
              <Select
                value={accessType}
                onValueChange={(next) => {
                  setAccessType(next)
                  if (!isContracted) {
                    return
                  }
                  const tpl = templates.find((item) => item.accessType === next)
                  const seeded = mergeContractFields(
                    schemaToContractFields(tpl?.parameters ?? []),
                    next === "READ" ? readFields : writeFields
                  )
                  if (next === "READ") {
                    setReadFields(seeded)
                    setWriteFields([])
                  } else {
                    setWriteFields(seeded)
                    setReadFields([])
                  }
                }}
                disabled={structureLocked}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="READ">READ</SelectItem>
                    <SelectItem value="WRITE">WRITE</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {!structureLocked && !isContracted ? (
              <p className="text-sm text-muted-foreground">
                WRITE 配置下发协议 JSON；READ 配置解析上报字段。platform / device / constant 来源的字段调用方不用传。
              </p>
            ) : isContracted ? (
              <p className="text-sm text-muted-foreground">
                参数名由 Modbus 契约锁死。寻址字段配常量或设备覆盖；写入值用 mapped，调用方只传业务简值。
              </p>
            ) : null}
            {!isFixed && !isCapabilityDefaultFn && !isContracted ? (
              <Field>
                <FieldLabel>载荷编码 payloadEncoding</FieldLabel>
                <Select
                  value={payloadEncoding}
                  onValueChange={(v) => setPayloadEncoding(v as "JSON" | "HEX" | "BINARY")}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="JSON">JSON — 对象/数组报文</SelectItem>
                      <SelectItem value="HEX">HEX — 按字段顺序紧排成空格分隔 hex</SelectItem>
                      <SelectItem value="BINARY">BINARY — 按字段顺序紧排成原始字节</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
                {payloadEncoding === "HEX" ? (
                  <ConfigExample title="示例 · Modbus 透传 hex 帧">
                    <p>
                      字段顺序即帧布局：area 1 字节、func 1 字节、offset 2 字节、quantity 2 字节。
                    </p>
                    <p>
                      值 0,0,0,1 打包为{" "}
                      <CodeSample>00 00 00 00 00 01</CodeSample>
                      ，MQTT 原样发布该字符串。
                    </p>
                  </ConfigExample>
                ) : payloadEncoding === "BINARY" ? (
                  <p className="text-xs text-muted-foreground">
                    与 HEX 相同按字段紧排，发布原始字节而不是 hex 文本。
                  </p>
                ) : null}
              </Field>
            ) : null}
            {isFixed ? (
              <>
                <FixedPropertyDescriptionEditor
                  label="功能参数 properties"
                  description="默认参数由模板规定，取值不可改；可改每项说明"
                  value={displayFixedProperties}
                  onChange={setCustomProperties}
                />
                <FixedWriteFieldEditor
                  label="写字段 writeFields"
                  description="field 与 optionValue 由模板规定不可改；可改字段/选项说明"
                  value={displayFixedWriteFields}
                  onChange={setWriteFields}
                />
                {displayFixedReadOptions.length > 0 ? (
                  <FixedValueOptionPicker
                    label="读值选项 readValueOptions"
                    description="optionValue 不可改，可改说明"
                    options={displayFixedReadOptions}
                    defaultOptionValue={
                      displayFixedReadOptions.find((item) => item.isDefault)?.optionValue
                    }
                    onDefaultChange={(optionValue) => {
                      setReadValueOptions(
                        displayFixedReadOptions.map((item) => ({
                          ...item,
                          isDefault: optionValue ? item.optionValue === optionValue : false,
                        }))
                      )
                    }}
                    onOptionsChange={setReadValueOptions}
                  />
                ) : (
                  <Field>
                    <FieldLabel>读值选项 readValueOptions</FieldLabel>
                    <p className="text-sm text-muted-foreground">当前无读值选项。</p>
                  </Field>
                )}
              </>
            ) : isCapabilityDefaultFn ? null : isContracted ? (
              <>
                {accessType === "WRITE" ? (
                  <Field>
                    <FieldLabel>载荷模式 payloadMode</FieldLabel>
                    <Select
                      value={payloadMode}
                      onValueChange={(v) => {
                        const next = v as "VALUE" | "STRUCT"
                        setPayloadMode(next)
                        setWriteFields((prev) =>
                          prev.map((row) =>
                            row.field === "value"
                              ? {
                                  ...row,
                                  source: next === "VALUE" ? "mapped" : "caller",
                                  ignoreRequest: next === "VALUE",
                                  callerField: next === "VALUE" ? row.callerField || "value" : undefined,
                                }
                              : row
                          )
                        )
                      }}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="STRUCT">STRUCT — 调用方按契约字段填</SelectItem>
                          <SelectItem value="VALUE">VALUE — 调用方只传业务简值</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    <ConfigExample title="示例 · 开灯">
                      <p>
                        functionId 填 <CodeSample>light.switch</CodeSample>，area=COIL、offset=10 配常量，
                        value 设 mapped。调用方传{" "}
                        <CodeSample>{`{ "value": "on" }`}</CodeSample>
                        。
                      </p>
                    </ConfigExample>
                  </Field>
                ) : null}
                <ContractFieldEditor
                  label={accessType === "READ" ? "读点位契约" : "写点位契约"}
                  description="字段名不可改。area/offset/dataType 建议 constant 或 device；WRITE 的 value 建议 mapped。"
                  schema={contractSchema}
                  value={accessType === "READ" ? readFields : writeFields}
                  onChange={accessType === "READ" ? setReadFields : setWriteFields}
                />
                {accessType === "WRITE" && payloadMode === "VALUE" ? (
                  <ValueMappingEditor
                    label="VALUE 映射"
                    description="调用方简值 → 写入寄存器/线圈的协议值，例如 on→true。patch path 填 value。"
                    value={valueMappings}
                    onChange={setValueMappings}
                    defaultPatchPath="value"
                  />
                ) : null}
              </>
            ) : accessType === "READ" ? (
              <WriteFieldListEditor
                label="读字段 readFields"
                description={
                  payloadEncoding === "JSON"
                    ? "从设备上报 JSON 里取哪些 path。字段名与协议 path 一致，如 temp、status.door。"
                    : "按字段顺序从 hex/二进制帧切片。每个叶子配置 byteLength，顺序即帧布局。"
                }
                value={readFields}
                onChange={setReadFields}
                showByteLayout={payloadEncoding !== "JSON"}
              />
            ) : (
              <>
                <Field>
                  <FieldLabel>载荷模式 payloadMode</FieldLabel>
                  <Select
                    value={payloadMode}
                    onValueChange={(v) => setPayloadMode(v as "VALUE" | "STRUCT")}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="STRUCT">STRUCT — 调用方按协议字段填</SelectItem>
                        <SelectItem value="VALUE">VALUE — 调用方只传业务简值</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  {payloadMode === "VALUE" ? (
                    <ConfigExample title="示例 · VALUE 远程控门">
                      <p>
                        调用方传{" "}
                        <CodeSample>{`{ "lock": "open" }`}</CodeSample>
                        ，需要两个参数时传{" "}
                        <CodeSample>{`{ "lock": "open", "mode": "night" }`}</CodeSample>
                        。
                      </p>
                      <p>
                        协议里 <CodeSample>params.0</CodeSample> 设为 mapped，调用方字段填{" "}
                        <CodeSample>lock</CodeSample>；下方映射把业务值 open 写成协议值 open。
                      </p>
                      <p>at / seq 用 platform 生成，devId 用 device 覆盖，密码用 constant，都不必出现在调用参数里。</p>
                    </ConfigExample>
                  ) : (
                    <ConfigExample title="示例 · STRUCT 直接填字段">
                      <p>
                        调用方按协议字段名传值，例如{" "}
                        <CodeSample>{`{ "command": "open" }`}</CodeSample>
                        。
                      </p>
                      <p>source=caller 的叶子来自请求；platform / device / constant 执行时自动填。</p>
                    </ConfigExample>
                  )}
                </Field>
                <FieldNodeTreeEditor
                  label="协议字段"
                  description={
                    payloadEncoding === "JSON"
                      ? "对应下发 JSON 的结构。数组用子字段名 0、1、2…，落库 path 为 params.0。"
                      : "按从上到下的字段顺序紧排成帧。每个叶子配置 byteLength；MQTT 发布组装后的 hex/二进制。"
                  }
                  value={structRoot}
                  onChange={setStructRoot}
                  showByteLayout={payloadEncoding !== "JSON"}
                />
                {payloadMode === "VALUE" ? (
                  <ValueMappingEditor
                    label="VALUE 映射"
                    description="调用方字段 + 业务值 → 写入协议 path。同一 callerField 的多条映射就是该参数的枚举。"
                    value={valueMappings}
                    onChange={setValueMappings}
                  />
                ) : null}
              </>
            )}
            {capabilityType === "MQTT" ? (
              <>
                <Field>
                  <FieldLabel htmlFor="publishTopicSlot">发布 Topic Slot</FieldLabel>
                  <Input
                    id="publishTopicSlot"
                    value={publishTopicSlot}
                    onChange={(e) => setPublishTopicSlot(e.target.value)}
                    placeholder="留空则用 default_pub；或填 topics 中的 slot 名"
                    disabled={accessType === "READ"}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="subscribeTopicSlot">订阅 Topic Slot</FieldLabel>
                  <Input
                    id="subscribeTopicSlot"
                    value={subscribeTopicSlot}
                    onChange={(e) => setSubscribeTopicSlot(e.target.value)}
                    placeholder="留空则用 default_sub；READ 功能常用"
                    disabled={accessType === "WRITE"}
                  />
                </Field>
                <p className="text-sm text-muted-foreground">
                  Topic 实际路径在设备 Address 里配（default_pub / default_sub / topics JSON）。这里只填 slot 名。
                </p>
                <Field>
                  <FieldLabel htmlFor="replyTopicSlot">应答 Topic Slot</FieldLabel>
                  <Input
                    id="replyTopicSlot"
                    value={replyTopicSlot}
                    onChange={(e) => setReplyTopicSlot(e.target.value)}
                    placeholder="设备回包订阅 slot，如 default_sub 或 topics 中的名"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="correlationPath">关联号 path</FieldLabel>
                  <Input
                    id="correlationPath"
                    value={correlationPath}
                    onChange={(e) => setCorrelationPath(e.target.value)}
                    placeholder="回包里 requestId 的字段，如 seq"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="resultPath">成败 path</FieldLabel>
                  <Input
                    id="resultPath"
                    value={resultPath}
                    onChange={(e) => setResultPath(e.target.value)}
                    placeholder="回包里表示成功/失败的字段，如 ok"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="replyTimeoutMs">应答超时（毫秒）</FieldLabel>
                  <Input
                    id="replyTimeoutMs"
                    type="number"
                    min={0}
                    value={replyTimeoutMs}
                    onChange={(e) => setReplyTimeoutMs(e.target.value)}
                    placeholder="留空则用流水线默认"
                  />
                </Field>
                <ConfigExample title="示例 · MQTT 应答闭环">
                  <p>
                    WRITE 发布 execute 后，把应答 slot 指到设备回包 topic。关联号 path 填
                    <CodeSample>seq</CodeSample>
                    ，与字段生成器 <CodeSample>request_id</CodeSample> 对应。
                  </p>
                  <p>READ 监听仍走订阅 slot；应答 slot 与订阅 slot 可以相同，未匹配的回包当遥测。</p>
                </ConfigExample>
              </>
            ) : null}
            <Field orientation="horizontal">
              <FieldLabel htmlFor="scheduleEnabled">定时下发</FieldLabel>
              <Switch
                id="scheduleEnabled"
                checked={scheduleEnabled}
                onCheckedChange={setScheduleEnabled}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="scheduleIntervalMs">定时间隔（毫秒）</FieldLabel>
              <Input
                id="scheduleIntervalMs"
                type="number"
                min={MIN_SCHEDULE_MS}
                value={scheduleIntervalMs}
                onChange={(e) => setScheduleIntervalMs(e.target.value)}
                placeholder={`最低 ${MIN_SCHEDULE_MS}，Modbus 轮询也走这里`}
              />
            </Field>
            <p className="text-sm text-muted-foreground">
              到期走现有下发队列。设备可再覆盖间隔或关掉。MQTT 有应答时，上一拍未完成会跳过本拍。
            </p>
            <Field>
              <FieldLabel htmlFor="sortIndex">排序</FieldLabel>
              <Input
                id="sortIndex"
                type="number"
                value={sortIndex}
                onChange={(e) => setSortIndex(e.target.value)}
              />
            </Field>
            {isCapabilityDefaultFn ? (
              <p className="rounded-md border border-dashed px-3 py-3 text-sm text-muted-foreground">
                能力预置功能（无参）结构已锁定，不可配置字段；仅可改说明与排序。
              </p>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFnOpen(false)}>
              取消
            </Button>
            {fnMode === "create" && templates.length > 0 ? (
              <Button variant="secondary" onClick={() => void importAll()} disabled={pending}>
                一键导入全部
              </Button>
            ) : null}
            <Button onClick={() => void saveFunction()} disabled={pending}>
              {fnMode === "edit" ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={viewProduct != null}
        onOpenChange={(next) => {
          if (!next) {
            setViewProduct(null)
            setProductFunctions([])
          }
        }}
      >
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              产品功能 · {viewProduct?.name || viewProduct?.code}
            </DialogTitle>
            <DialogDescription>查看、创建、编辑或移除该产品下的功能。</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end">
            <Button
              size="sm"
              onClick={() => viewProduct && openCreateFunction(viewProduct)}
              disabled={!viewProduct}
            >
              <PlusIcon data-icon="inline-start" />
              创建功能
            </Button>
          </div>
          {functionsLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2Icon className="size-4 animate-spin" />
              加载中…
            </div>
          ) : productFunctions.length === 0 ? (
            <Empty className="border border-dashed">
              <EmptyHeader>
                <EmptyTitle>尚未挂载功能</EmptyTitle>
                <EmptyDescription>点击「创建功能」或使用一键导入。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>functionId</TableHead>
                  <TableHead>说明</TableHead>
                  <TableHead>能力</TableHead>
                  <TableHead>访问</TableHead>
                  <TableHead className="w-28">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {productFunctions.map((fn) => (
                  <TableRow key={fn.id || fn.functionId}>
                    <TableCell className="font-mono text-sm">{fn.functionId}</TableCell>
                    <TableCell>{fn.description || "—"}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{fn.capabilityType || "—"}</Badge>
                    </TableCell>
                    <TableCell>{fn.accessType}</TableCell>
                    <TableCell className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={pending}
                        onClick={() => openEditFunction(fn)}
                      >
                        编辑
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={pending}
                        onClick={() => void removeFunction(fn.functionId)}
                      >
                        移除
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setViewProduct(null)}>
              关闭
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
