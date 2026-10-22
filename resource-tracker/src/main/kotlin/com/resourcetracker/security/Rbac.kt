package com.resourcetracker.security

import com.resourcetracker.common.ForbiddenRoleException
import com.resourcetracker.user.Role
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresRoles(vararg val value: Role)

class RoleAuthorizationInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if(handler !is HandlerMethod) return true

        val required = handler.getMethodAnnotation(RequiresRoles::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(handler.beanType, RequiresRoles::class.java)

        val caller = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        if (caller == null) {
            return required == null || request.requestURI.startsWith("/api/v1/auth/")
        }
        // Authentication is enforced by Spring Security for protected routes.
        // Only apply a role restriction when the handler explicitly declares one.
        if (required == null) return true
        if (caller.role !in required.value) throw ForbiddenRoleException(required.value.toList(), caller.role)
        return true
    }
}
@Configuration
class webConfig : WebMvcConfigurer{
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RoleAuthorizationInterceptor())
    }
}
