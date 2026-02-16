package com.clinicalflow.utils

object PiiScrubber {
    
    private val patterns = listOf(
        // Names with titles
        Regex("(?i)\\b(mr|mrs|ms|shri|smt|dr)\\.?\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?\\b"),
        
        // Age with context
        Regex("(?i)\\b(age|aged|y/o|year-old|years old)\\s*[:=]?\\s*\\d{1,3}\\b"),
        Regex("(?i)\\b\\d{1,3}\\s*(y/o|year-old|years old)\\b"),
        
        // Phone numbers
        Regex("\\b(?:\\+?1[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"),
        
        // MRN patterns
        Regex("(?i)\\b(mrn|medical record|patient id|patient #)\\s*[:=]?\\s*[A-Z0-9-]+\\b", RegexOption.IGNORE_CASE),
        
        // Dates of birth
        Regex("(?i)\\b(dob|date of birth|born)\\s*[:=]?\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b"),
        
        // SSN
        Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        
        // Email
        Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        
        // Addresses (basic)
        Regex("\\d+\\s+[A-Za-z]+(?:\\s+[A-Za-z]+)*\\s+(?:street|st|avenue|ave|road|rd|drive|dr|lane|ln|boulevard|blvd)\\b", RegexOption.IGNORE_CASE)
    )
    
    private val replacements = mapOf(
        "name" to "[NAME]",
        "age" to "[AGE]",
        "phone" to "[PHONE]",
        "mrn" to "[MRN]",
        "dob" to "[DOB]",
        "ssn" to "[SSN]",
        "email" to "[EMAIL]",
        "address" to "[ADDRESS]"
    )
    
    /**
     * Scrubs PII from text. Returns pair of (scrubbedText, foundPiiTypes)
     */
    fun scrub(text: String): Pair<String, Set<String>> {
        var result = text
        val foundTypes = mutableSetOf<String>()
        
        // Scrub names
        patterns[0].findAll(text).forEach {
            result = result.replace(it.value, "[NAME]")
            foundTypes.add("name")
        }
        
        // Scrub ages
        patterns[1].findAll(text).forEach {
            result = result.replace(it.value, "[AGE]")
            foundTypes.add("age")
        }
        patterns[2].findAll(text).forEach {
            result = result.replace(it.value, "[AGE]")
            foundTypes.add("age")
        }
        
        // Scrub phone numbers
        patterns[3].findAll(text).forEach {
            result = result.replace(it.value, "[PHONE]")
            foundTypes.add("phone")
        }
        
        // Scrub MRNs
        patterns[4].findAll(text).forEach {
            result = result.replace(it.value, "[MRN]")
            foundTypes.add("mrn")
        }
        
        // Scrub DOBs
        patterns[5].findAll(text).forEach {
            result = result.replace(it.value, "[DOB]")
            foundTypes.add("dob")
        }
        
        // Scrub SSNs
        patterns[6].findAll(text).forEach {
            result = result.replace(it.value, "[SSN]")
            foundTypes.add("ssn")
        }
        
        // Scrub emails
        patterns[7].findAll(text).forEach {
            result = result.replace(it.value, "[EMAIL]")
            foundTypes.add("email")
        }
        
        // Scrub addresses
        patterns[8].findAll(text).forEach {
            result = result.replace(it.value, "[ADDRESS]")
            foundTypes.add("address")
        }
        
        return result to foundTypes
    }
    
    /**
     * Scrub for study mode - removes patient context entirely
     */
    fun scrubForStudy(text: String): String {
        var result = text
        
        // Replace patient identifiers with generic terms
        result = result.replace(Regex("(?i)patient\\s+[A-Z][a-z]+"), "the patient")
        result = result.replace(Regex("(?i)\\b[A-Z][a-z]+,\\s*(a\\s+)?\\d+[- ]?year[- ]?old"), "The patient")
        
        // Use standard scrub for remaining PII
        return scrub(result).first
    }
}