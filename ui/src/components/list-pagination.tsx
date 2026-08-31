import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"

type Props = {
  page: number
  totalPages: number
  total: number
  onPageChange: (page: number) => void
}

export function ListPagination({ page, totalPages, total, onPageChange }: Props) {
  if (totalPages <= 1) {
    return (
      <p className="text-sm text-muted-foreground">
        共 {total} 条
      </p>
    )
  }

  const pages = visiblePages(page, totalPages)

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted-foreground">
        第 {page}/{totalPages} 页 · 共 {total} 条
      </p>
      <Pagination className="mx-0 w-auto justify-start sm:justify-end">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              text="上一页"
              href="#"
              aria-disabled={page <= 1}
              className={page <= 1 ? "pointer-events-none opacity-50" : undefined}
              onClick={(event) => {
                event.preventDefault()
                if (page > 1) {
                  onPageChange(page - 1)
                }
              }}
            />
          </PaginationItem>
          {pages.map((item, index) =>
            item === "…" ? (
              <PaginationItem key={`ellipsis-${index}`}>
                <span className="px-2 text-muted-foreground">…</span>
              </PaginationItem>
            ) : (
              <PaginationItem key={item}>
                <PaginationLink
                  href="#"
                  isActive={item === page}
                  onClick={(event) => {
                    event.preventDefault()
                    onPageChange(item)
                  }}
                >
                  {item}
                </PaginationLink>
              </PaginationItem>
            )
          )}
          <PaginationItem>
            <PaginationNext
              text="下一页"
              href="#"
              aria-disabled={page >= totalPages}
              className={page >= totalPages ? "pointer-events-none opacity-50" : undefined}
              onClick={(event) => {
                event.preventDefault()
                if (page < totalPages) {
                  onPageChange(page + 1)
                }
              }}
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  )
}

function visiblePages(page: number, totalPages: number): Array<number | "…"> {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1)
  }
  const result: Array<number | "…"> = [1]
  const start = Math.max(2, page - 1)
  const end = Math.min(totalPages - 1, page + 1)
  if (start > 2) {
    result.push("…")
  }
  for (let current = start; current <= end; current += 1) {
    result.push(current)
  }
  if (end < totalPages - 1) {
    result.push("…")
  }
  result.push(totalPages)
  return result
}
