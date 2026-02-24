package com.fancia.backend.user.mapper

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.user.core.dto.CreateUserRequest
import com.fancia.backend.user.core.dto.UpdateUserRequest
import com.fancia.backend.user.core.dto.UserResponse
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget
import org.mapstruct.NullValueMappingStrategy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
    nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT
)
interface UserMapper {
    fun toDto(user: User): UserResponse
    fun toBean(request: CreateUserRequest): User
    fun toBean(request: UpdateUserRequest): User
    fun toBean(request: UpdateUserRequest, @MappingTarget target: User): User
    fun map(authorities: Collection<GrantedAuthority>): List<String> =
        authorities.mapNotNull { it.authority }.toList()

    fun map(names: List<String>): Collection<GrantedAuthority> =
        names.filter { it.isNotBlank() }.map(::SimpleGrantedAuthority)
}