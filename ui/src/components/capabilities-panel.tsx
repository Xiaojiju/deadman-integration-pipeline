import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { catalogModeLabel } from "@/lib/catalog-mode"
import type { CapabilityDescriptor } from "@/lib/types"

type Props = {
  capabilities: CapabilityDescriptor[]
}

export function CapabilitiesPanel({ capabilities }: Props) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>能力</CardTitle>
        <CardDescription>
          FIXED：封闭 API 集，配通道即可。CONTRACT：业务 functionId 可自定义，参数名锁死。OPEN：协议字段结构可自定义。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {capabilities.length === 0 ? (
          <Empty className="border border-dashed">
            <EmptyHeader>
              <EmptyTitle>暂无能力</EmptyTitle>
              <EmptyDescription>确认网关已启动并完成能力 register。</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <div className="flex flex-col gap-6">
            {capabilities.map((cap) => (
              <div key={cap.capabilityType} className="flex flex-col gap-3">
                <div className="flex items-center gap-2">
                  <h3 className="font-medium">{cap.capabilityType}</h3>
                  <Badge variant="secondary">
                    {catalogModeLabel(cap.functionMode)}
                  </Badge>
                  <Badge variant="outline">
                    {cap.functionTemplates?.length ?? 0} 功能
                  </Badge>
                </div>
                {(cap.functionTemplates?.length ?? 0) === 0 ? (
                  <p className="text-sm text-muted-foreground">无预置功能模板</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>functionId</TableHead>
                        <TableHead>说明</TableHead>
                        <TableHead>访问</TableHead>
                        <TableHead>参数</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {cap.functionTemplates.map((fn) => (
                        <TableRow key={fn.functionId}>
                          <TableCell className="font-mono text-sm">{fn.functionId}</TableCell>
                          <TableCell>{fn.description || "—"}</TableCell>
                          <TableCell>{fn.accessType}</TableCell>
                          <TableCell className="text-muted-foreground">
                            {(fn.parameters ?? []).map((p) => p.name).join(", ") || "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
