package com.resourcetracker.common

data class DepartmentReference(val id: Int, val name: String)

data class UserReference(val id: Int, val name: String)

data class TeamReference(val id: Int, val name: String)

data class ProjectReference(val id: Int, val name: String, val code: String)

data class VendorReference(val id: Int, val name: String)

data class ResourceReference(val id: Int, val code: String, val name: String)
