import { useAuthStore } from '~/stores/auth'

export default defineNuxtRouteMiddleware(async () => {
  const auth = useAuthStore()
  await auth.bootstrap()
  if (!auth.isAuthenticated) {
    return navigateTo('/login')
  }
})
