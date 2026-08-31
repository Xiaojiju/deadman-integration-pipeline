import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import { CapabilitiesPanel } from "@/components/capabilities-panel"
import { ChannelsPanel } from "@/components/channels-panel"
import { DevicesPanel } from "@/components/devices-panel"
import { ProductsPanel } from "@/components/products-panel"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Toaster } from "@/components/ui/sonner"
import { catalogApi } from "@/lib/api"
import type {
  CapabilityDescriptor,
  ChannelEntity,
  ProductEntity,
} from "@/lib/types"

export function App() {
  const [capabilities, setCapabilities] = useState<CapabilityDescriptor[]>([])
  const [products, setProducts] = useState<ProductEntity[]>([])
  const [channels, setChannels] = useState<ChannelEntity[]>([])
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const [caps, prods, chans] = await Promise.all([
        catalogApi.listCapabilities(),
        catalogApi.listProducts(1, 100),
        catalogApi.listChannels(1, 100),
      ])
      setCapabilities(caps)
      setProducts(prods.items)
      setChannels(chans.items)
      setError(null)
    } catch (err) {
      const message = err instanceof Error ? err.message : "无法连接网关 API"
      setError(message)
      toast.error(message)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const productMap = useMemo(() => {
    return new Map(products.map((item) => [item.id, item]))
  }, [products])

  return (
    <div className="min-h-svh bg-background">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-4 py-8 sm:px-6">
        <header className="flex flex-col gap-3">
          <p className="text-sm font-medium tracking-[0.18em] text-muted-foreground uppercase">
            Deadman Integration Pipeline
          </p>
          <h1 className="font-heading text-4xl font-semibold tracking-tight sm:text-5xl">
            MTFM Gateway
          </h1>
          <p className="max-w-2xl text-muted-foreground">
            配置产品、通道与设备，动态加载到运行时，并手动下发南向指令。
          </p>
        </header>

        <Separator />

        {error ? (
          <Alert variant="destructive">
            <AlertTitle>API 不可用</AlertTitle>
            <AlertDescription>
              {error}。请先启动网关（默认 :8080），或确认 Vite 代理目标正确。
            </AlertDescription>
          </Alert>
        ) : null}

        <Tabs defaultValue="devices">
          <TabsList>
            <TabsTrigger value="devices">设备</TabsTrigger>
            <TabsTrigger value="products">产品</TabsTrigger>
            <TabsTrigger value="channels">通道</TabsTrigger>
            <TabsTrigger value="capabilities">能力</TabsTrigger>
          </TabsList>
          <TabsContent value="devices" className="mt-4">
            <DevicesPanel
              products={products}
              channels={channels}
              capabilities={capabilities}
              productMap={productMap}
            />
          </TabsContent>
          <TabsContent value="products" className="mt-4">
            <ProductsPanel
              productOptions={products}
              capabilities={capabilities}
              onChanged={() => void refresh()}
            />
          </TabsContent>
          <TabsContent value="channels" className="mt-4">
            <ChannelsPanel
              channels={channels}
              capabilities={capabilities}
              onChanged={() => void refresh()}
            />
          </TabsContent>
          <TabsContent value="capabilities" className="mt-4">
            <CapabilitiesPanel capabilities={capabilities} />
          </TabsContent>
        </Tabs>
      </div>
      <Toaster richColors closeButton />
    </div>
  )
}

export default App
