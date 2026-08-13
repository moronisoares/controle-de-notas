package com.example.controlenotas

import android.app.Application
import com.example.controlenotas.util.Notifications
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ControleNotasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Necessário para a leitura de texto dos PDFs (carrega as fontes do PDFBox).
        PDFBoxResourceLoader.init(applicationContext)
        Notifications.createChannels(this)
    }
}
