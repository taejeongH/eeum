import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/services/api'
import { useFamilyStore } from '@/stores/family'
import { Logger } from '@/services/logger'

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

    // Track deleted IDs for backend sync
    const deletedMedicationIds = ref([])

    const reset = () => {
        isInitialized.value = false
        currentFamilyId.value = null
        groupName.value = ''
        seniorId.value = null
        bloodType.value = ''
        diseases.value = []
        contactSlots.value = [null, null, null]
        medications.value = []
        deletedMedicationIds.value = []
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

        } catch (error) {
            Logger.error('그룹 기본 정보/구성원 조회 실패:', error)
        }

        try {
            // 2-1. Fetch Members & Details
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
                }
            }

            // 3. Emergency Contacts
            // Fetch Family Details to get memberPriorities
            const familyDetailRes = await api.get(`/families/${familyId}/details`)
            const familyDetail = familyDetailRes.data



            if (familyDetail && familyDetail.memberPriorities) {

                familyDetail.memberPriorities.forEach((p) => {
                    const priorityIndex = p.emergencyPriority - 1
                    if (priorityIndex >= 0 && priorityIndex < 3) {
                        // Find member by userId
                        const fullMember = members.find(m => (m.userId || m.id) === p.userId)
                        if (fullMember) {
                            contactSlots.value[priorityIndex] = fullMember
                        }
                    }
                })
            } else {

            }

            // 4. Fetch Medications (New API)
            const medRes = await api.get(`/families/${familyId}/medications`)
            if (medRes.data && medRes.data.length > 0) {

                medications.value = medRes.data.map(m => ({
                    ...m,
                    // Ensure format matches what UI expects if needed (e.g. time strings)
                    // The DTO response has notificationTimes as Array<string>, which matches UI.
                }))
            } else {

                medications.value = []
            }

        } catch (error) {
            Logger.error('그룹 설정 데이터 초기화 실패:', error)
            reset()
        }
    }

    const addMedication = (med) => {
        // Calculate totalDosesDay
        const totalDosesDay = med.notificationTimes ? med.notificationTimes.length : 0
        medications.value.push({
            ...med,
            totalDosesDay
        })
    }

    const removeMedication = (index) => {
        const target = medications.value[index]
        if (target.id) {
            // It's an existing medication (persistently saved), mark for deletion
            deletedMedicationIds.value.push(target.id)
        }
        medications.value.splice(index, 1)
    }

    const saveData = async (familyId) => {
        try {
            // 1. Construct Payload for Group Update
            const priorities = []
            contactSlots.value.forEach((member, index) => {
                if (member) {
                    priorities.push({
                        userId: member.userId || member.id,
                        emergencyPriority: index + 1
                    })
                }
            })

            const payload = {
                newGroupName: groupName.value,
                dependentUserId: seniorId.value,
                dependentBloodType: bloodType.value,
                dependentChronicDiseases: diseases.value,
                memberPriorities: priorities
            }



            // 2. Call API (Group Info)
            await api.put(`/families/${familyId}`, payload)


            // 3. Handle Medications

            // 3-1. Delete removed medications
            if (deletedMedicationIds.value.length > 0) {

                await Promise.all(deletedMedicationIds.value.map(id =>
                    api.delete(`/families/${familyId}/medications/${id}`)
                ))
            }

            // 3-2. Create NEW medications only (those without IDs)
            const newMedications = medications.value.filter(m => !m.id)

            if (newMedications.length > 0) {

                const medPayload = newMedications.map(m => ({
                    medicineName: m.medicineName,
                    cycleType: m.cycleType,
                    totalDosesDay: m.totalDosesDay,
                    cycleValue: m.cycleValue,
                    daysOfWeek: m.daysOfWeek,
                    startDate: m.startDate,
                    endDate: m.endDate,
                    notificationTimes: m.notificationTimes
                }))

                await api.post(`/families/${familyId}/medications`, medPayload)

            }

        } catch (error) {
            Logger.error('그룹 설정 저장 실패:', error)
            throw error;
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
        saveData,
        addMedication,
        removeMedication,
        reset
    }
})
