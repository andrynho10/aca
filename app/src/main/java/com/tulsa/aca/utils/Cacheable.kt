package com.tulsa.aca.utils

/**
 * Interfaz para ViewModels que manejan caché
 * Permite un manejo uniforme y thread-safe del caché
 */
interface Cacheable {
    /**
     * Limpia el caché interno del componente
     */
    fun limpiarCache()

    /**
     * Obtiene información del estado del caché para debugging
     */
    fun obtenerInfoCache(): String

    /**
     * Verifica si el caché está expirado
     */
    fun esCacheExpirado(): Boolean
}