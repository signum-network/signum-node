import { getRouteApi } from '@tanstack/react-router'
import { AccountDetail } from '@/components/account'

const route = getRouteApi('/accounts/$id')

export default function AccountPage() {
  const { id } = route.useParams()
  return <AccountDetail key={id} id={id} />
}
