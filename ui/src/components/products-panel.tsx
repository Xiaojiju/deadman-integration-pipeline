import { Loader2Icon, PlusIcon, RefreshCwIcon } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import {
  KeyValueListEditor,
  keyValueRowsToRecord,
  recordToKeyValueRows,
  type KeyValueRow,
} from "@/components/key-value-list-editor"
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
import {
  ValueOptionListEditor,
  filterValidValueOptions,
} from "@/components/value-option-list-editor"
import {
  WriteFieldListEditor,
  filterValidWriteFields,
} from "@/components/write-field-list-editor"
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
import { catalogApi } from "@/lib/api"
import type {
  CapabilityDescriptor,
  ProductEntity,
  ProductFunctionEntity,
  PropertyItem,
  SchemaField,
  ValueOption,
  WriteFieldOption,
} from "@/lib/types"

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
  const [writeAccessType, setWriteAccessType] = useState<"VALUE" | "STRUCT">("VALUE")
  const [sortIndex, setSortIndex] = useState("0")
  const [customProperties, setCustomProperties] = useState<PropertyItem[]>([])
  const [writeValueOptions, setWriteValueOptions] = useState<ValueOption[]>([])
  const [writeFields, setWriteFields] = useState<WriteFieldOption[]>([])
  const [readValueOptions, setReadValueOptions] = useState<ValueOption[]>([])
  const [protocolRows, setProtocolRows] = useState<KeyValueRow[]>([])
  const [fnDescription, setFnDescription] = useState("")

  const selectedCapability = capabilities.find((item) => item.capabilityType === capabilityType)
  const templates = selectedCapability?.functionTemplates ?? []
  const isFixed = selectedCapability?.functionMode === "FIXED"
  const selectedTemplate = templates.find((item) => item.functionId === functionId)
  const paramSchema: SchemaField[] = useMemo(
    () => selectedTemplate?.parameters ?? [],
    [selectedTemplate]
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

  function openCreateFunction(forProduct?: ProductEntity) {
    const target = forProduct ?? viewProduct
    setFnMode("create")
    setEditingFunction(null)
    setProductId(target?.id ?? productOptions[0]?.id ?? "")
    setCapabilityType(capabilities[0]?.capabilityType ?? "")
    setFunctionId("")
    setAccessType("WRITE")
    setWriteAccessType("VALUE")
    setSortIndex("0")
    setCustomProperties([])
    setWriteValueOptions([])
    setWriteFields([])
    setReadValueOptions([])
    setProtocolRows([])
    setFnDescription("")
    setFnOpen(true)
  }

  function openEditFunction(fn: ProductFunctionEntity) {
    setFnMode("edit")
    setEditingFunction(fn)
    setProductId(fn.productId)
    setCapabilityType(fn.capabilityType ?? "")
    setFunctionId(fn.functionId)
    setAccessType(fn.accessType || "WRITE")
    setWriteAccessType(fn.writeAccessType === "STRUCT" ? "STRUCT" : "VALUE")
    setSortIndex(String(fn.sortIndex ?? 0))
    setFnDescription(fn.description ?? "")
    setCustomProperties(fn.properties ?? [])
    const mapping =
      typeof fn.protocolMapping === "string"
        ? (() => {
            try {
              return JSON.parse(fn.protocolMapping) as Record<string, unknown>
            } catch {
              return {}
            }
          })()
        : (fn.protocolMapping ?? {})
    setProtocolRows(recordToKeyValueRows(mapping))
    setWriteValueOptions(fn.writeValueOptions ?? [])
    setWriteFields(fn.writeFields ?? [])
    setReadValueOptions(fn.readValueOptions ?? [])
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
    setWriteValueOptions([])
    setReadValueOptions([])
    setWriteAccessType("STRUCT")
  }

  function onCapabilityOrFunctionChange(nextCapability: string, nextFunctionId: string) {
    setCapabilityType(nextCapability)
    setFunctionId(nextFunctionId)
    const cap = capabilities.find((item) => item.capabilityType === nextCapability)
    const template = cap?.functionTemplates.find((item) => item.functionId === nextFunctionId)
    if (fnMode === "create") {
      seedOptionsFromTemplate(template)
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
    const properties = isFixed ? displayFixedProperties : undefined
    const fixedFields = isFixed ? displayFixedWriteFields : []
    const writeOpts = isFixed ? [] : filterValidValueOptions(writeValueOptions)
    const structFields = isFixed ? fixedFields : filterValidWriteFields(writeFields)
    const readOpts = filterValidValueOptions(readValueOptions)
    const protocolMapping = isFixed ? {} : keyValueRowsToRecord(protocolRows)

    setPending(true)
    try {
      const body = {
        accessType,
        properties,
        writeAccessType: isFixed ? "STRUCT" : writeAccessType,
        writeValueOptions: (isFixed || writeAccessType !== "VALUE") ? [] : writeOpts,
        writeFields: (isFixed || writeAccessType === "STRUCT") ? structFields : [],
        readValueOptions: readOpts,
        protocolMapping: Object.keys(protocolMapping).length ? protocolMapping : undefined,
        sortIndex: Number(sortIndex) || 0,
        description: fnDescription.trim() || undefined,
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
            同质设备模板。可创建/编辑产品与功能；FIXED 能力可一键导入，OPEN 能力可自定义功能参数。
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
                          {item.functionMode === "FIXED" ? " · FIXED" : " · OPEN"}
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
                ? "FIXED：默认参数与写字段的 field/取值不可改，可改说明；OPEN：可维护属性与选项。"
                : "FIXED：从预置模板选择，参数与写字段由模板规定。OPEN：可自定义 functionId 与写选项。"}
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
                        {item.functionMode === "FIXED" ? " · FIXED" : " · OPEN"}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {fnMode === "create" && (isFixed || templates.length > 0) ? (
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
                  placeholder="例如 pump.readTemp"
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
                onValueChange={setAccessType}
                disabled={isFixed}
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
            {!isFixed ? (
              <Field>
                <FieldLabel>写访问模式 writeAccessType</FieldLabel>
                <Select
                  value={writeAccessType}
                  onValueChange={(value) => setWriteAccessType(value as "VALUE" | "STRUCT")}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="VALUE">VALUE（标量选项）</SelectItem>
                      <SelectItem value="STRUCT">STRUCT（多字段）</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
            <Field>
              <FieldLabel htmlFor="sortIndex">排序</FieldLabel>
              <Input
                id="sortIndex"
                type="number"
                value={sortIndex}
                onChange={(e) => setSortIndex(e.target.value)}
              />
            </Field>
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
            ) : writeAccessType === "VALUE" ? (
              <ValueOptionListEditor
                label="写值选项 writeValueOptions"
                description="下发时的枚举按钮，如 open / close"
                value={writeValueOptions}
                onChange={setWriteValueOptions}
              />
            ) : (
              <WriteFieldListEditor
                label="写字段 writeFields"
                description="STRUCT 模式下每个字段及其可选值"
                value={writeFields}
                onChange={setWriteFields}
              />
            )}
            {!isFixed ? (
              <>
                <ValueOptionListEditor
                  label="读值选项 readValueOptions"
                  description="上报值 ↔ 业务映射（可选）"
                  value={readValueOptions}
                  onChange={setReadValueOptions}
                />
                <KeyValueListEditor
                  label="协议映射 protocolMapping"
                  description="协议层参数（可选），如寄存器偏移"
                  value={protocolRows}
                  onChange={setProtocolRows}
                  keyLabel="映射字段名 key"
                  valueLabel="映射字段值 value"
                  keyPlaceholder="例如 holdingOffset"
                  valuePlaceholder="例如 100"
                />
              </>
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
