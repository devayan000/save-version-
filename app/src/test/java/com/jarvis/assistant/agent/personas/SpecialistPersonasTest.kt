package com.jarvis.assistant.agent.personas

import org.junit.Assert.*
import org.junit.Test

class SpecialistPersonasTest {
    @Test fun codeRequestSelectsCoder() { assertEquals(SpecialistPersonas.Persona.CODER, SpecialistPersonas.select("write a function in python")) }
    @Test fun emotionalRequestSelectsTherapist() { assertEquals(SpecialistPersonas.Persona.THERAPIST, SpecialistPersonas.select("I feel stressed and worried")) }
    @Test fun learningRequestSelectsTutor() { assertEquals(SpecialistPersonas.Persona.TUTOR, SpecialistPersonas.select("teach me about photosynthesis")) }
    @Test fun genericRequestSelectsGeneral() { assertEquals(SpecialistPersonas.Persona.GENERAL, SpecialistPersonas.select("set a timer")) }
    @Test fun generalPersonaHasEmptySystemBlock() { assertEquals("", SpecialistPersonas.Persona.GENERAL.systemBlock) }
}
