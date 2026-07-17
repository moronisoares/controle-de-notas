package com.example.controlenotas.data

/** Categorias fixas de despesa exibidas ao usuário. */
enum class Category(val displayName: String) {
    AGUA("Água"),
    LUZ("Luz"),
    INTERNET("Internet"),
    ALIMENTACAO("Alimentação"),
    DESPESAS_MEDICAS("Despesas médicas"),
    CURSOS_TREINAMENTOS("Cursos e treinamentos");

    companion object {
        fun fromName(name: String): Category =
            entries.firstOrNull { it.name == name } ?: AGUA
    }
}
