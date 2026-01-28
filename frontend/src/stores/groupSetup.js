import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/services/api'
import { useFamilyStore } from '@/stores/family'

export const useGroupSetupStore = defineStore('groupSetup', () => {
    const isInitialized = ref(false)
    const currentFamilyId = ref(null)

    // Step 1: Group Name
    const groupName = ref('')

    // Step 2: Health Info
    const seniorId = ref(null)
    const bloodType = ref('')
    const diseases = ref([])

    // Step 3: Emergency Contacts
    const contactSlots = ref([null, null, null])

    // Step 4: Medication
    const medications = ref([])

    const reset = () => {
        isInitialized.value = false
        currentFamilyId.value = null
        groupName.value = ''
        seniorId.value = null
        bloodType.value = ''
        diseases.value = []
        contactSlots.value = [null, null, null]
        medications.value = []
    }

    // Actions
    const initData = async (familyId) => {
        const familyStore = useFamilyStore()

        // If already initialized for this family, do nothing (preserve edits)
        if (isInitialized.value && currentFamilyId.value === familyId) {
            return
        }

        reset() // Clear old data before fetch
        currentFamilyId.value = familyId
        isInitialized.value = true

        try {
            // 1. Fetch Group Basic Info (Name)
            if (familyStore.families.length === 0) {
                await familyStore.fetchFamilies()
            }
            const family = familyStore.families.find(f => f.id == familyId)
            if (family) {
                groupName.value = family.name
            }

            // 2. Fetch Members & Details
            const membersRes = await api.get(`/families/${familyId}/members`)
            const members = membersRes.data

            // Auto-fill Health Info & Medications from Dependent Profile
            const targetMember = members.find(m => m.dependent === true)
            if (targetMember) {
                seniorId.value = targetMember.userId || targetMember.id

                // Fetch Detail
                const detailRes = await api.get(`/families/${familyId}/members/${seniorId.value}`)
                const detail = detailRes.data
                if (detail) {
                    if (detail.bloodType) bloodType.value = detail.bloodType

                    // Handle diseases safely (API field: chronicDiseases)
                    const sourceDiseases = detail.chronicDiseases || detail.diseases

                    if (sourceDiseases) {
                        console.log('Raw diseases data:', sourceDiseases)
                        if (Array.isArray(sourceDiseases)) {
                            diseases.value = sourceDiseases
                        } else if (typeof sourceDiseases === 'string') {
                            // Split by comma and clean up whitespace
                            diseases.value = sourceDiseases.split(',').map(d => d.trim()).filter(d => d)
                        } else {
                            diseases.value = []
                        }
                    } else {
                        diseases.value = []
                    }

                    if (detail.medications) medications.value = detail.medications
                }
            }

            // 3. Emergency Contacts (Likely inside family object if supported, else leave empty or implement logic later)
            // Existing logic in Step3 was looking at `family.emergencyContacts`.
            if (family && family.emergencyContacts) {
                family.emergencyContacts.forEach((contact) => {
                    const priorityIndex = contact.priority - 1
                    if (priorityIndex >= 0 && priorityIndex < 3) {
                        const fullMember = members.find(m => m.userId === contact.memberId)
                        if (fullMember) {
                            contactSlots.value[priorityIndex] = fullMember
                        }
                    }
                })
            }

        } catch (error) {
            console.error('Failed to init group setup data:', error)
            reset() // Reset partly faulty state? Or keep simple
        }
    }

    return {
        isInitialized,
        currentFamilyId,
        groupName,
        seniorId,
        bloodType,
        diseases,
        contactSlots,
        medications,
        initData,
        reset
    }
})
