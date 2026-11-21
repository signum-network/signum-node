import { getRouteApi } from '@tanstack/react-router'
import { Blocks } from '@/components/blocks'

const route = getRouteApi('/blocks')

export default function BlocksPage() {
  const { page } = route.useSearch()
  return <Blocks page={page} />
}
