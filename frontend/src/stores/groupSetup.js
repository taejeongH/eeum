import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/services/api'
import { useFamilyStore } from '@/stores/family'
import { Logger } from '@/services/logger'

/**
 * 그룹 설정(기본 정보, 질환, 비상 연락처, 복약 정보)을 관리하는 Pinia 스토어입니다.
 * @summary 그룹 설정 관리 스토어
 */
export const useGroupSetupStore = defineStore('groupSetup', () => {
  /** @type {import('vue').Ref<boolean>} 초기화 여부 */
  const isInitialized = ref(false);

    
    const groupName = ref('')

    
    const seniorId = ref(null)
    const bloodType = ref('')
    const diseases = ref([])

    
    const contactSlots = ref([null, null, null])

    
    const medications = ref([])

    
    const deletedMedicationIds = ref([])

  /**
   * 스토어 상태를 초기화합니다.
   */
  const reset = () => {
    isInitialized.value = false;
    currentFamilyId.value = null;
    groupName.value = '';
    seniorId.value = null;
    bloodType.value = '';
    diseases.value = [];
    contactSlots.value = [null, null, null];
    medications.value = [];
    deletedMedicationIds.value = [];
  };

  /**
   * 특정 가족의 설정을 위한 데이터를 초기화합니다.
   * @param {number|string} familyId 가족 ID
   */
  const initData = async (familyId) => {
    if (isAlreadyInitialized(familyId)) return;

    reset();
    currentFamilyId.value = familyId;
    isInitialized.value = true;

    try {
      await Promise.all([
        fetchGroupBasicInfo(familyId),
        fetchDependentProfileAndHealth(familyId),
        fetchEmergencyContacts(familyId),
        fetchMedications(familyId),
      ]);
    } catch (error) {
      handleInitError(error);
    }
  };

    
    const initData = async (familyId) => {
        const familyStore = useFamilyStore()

        
        if (isInitialized.value && currentFamilyId.value === familyId) {
            return
        }
      });
    }
  };

        reset() 
        currentFamilyId.value = familyId
        isInitialized.value = true

        try {
            
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
            
            const membersRes = await api.get(`/families/${familyId}/members`)
            const members = membersRes.data

            
            const targetMember = members.find(m => m.dependent === true)
            if (targetMember) {
                seniorId.value = targetMember.userId || targetMember.id

                
                const detailRes = await api.get(`/families/${familyId}/members/${seniorId.value}`)
                const detail = detailRes.data
                if (detail) {
                    if (detail.bloodType) bloodType.value = detail.bloodType

                    
                    const sourceDiseases = detail.chronicDiseases || detail.diseases

                    if (sourceDiseases) {

                        if (Array.isArray(sourceDiseases)) {
                            diseases.value = sourceDiseases
                        } else if (typeof sourceDiseases === 'string') {
                            
                            diseases.value = sourceDiseases.split(',').map(d => d.trim()).filter(d => d)
                        } else {
                            diseases.value = []
                        }
                    } else {
                        diseases.value = []
                    }
                }
            }

            
            
            const familyDetailRes = await api.get(`/families/${familyId}/details`)
            const familyDetail = familyDetailRes.data



            if (familyDetail && familyDetail.memberPriorities) {

                familyDetail.memberPriorities.forEach((p) => {
                    const priorityIndex = p.emergencyPriority - 1
                    if (priorityIndex >= 0 && priorityIndex < 3) {
                        
                        const fullMember = members.find(m => (m.userId || m.id) === p.userId)
                        if (fullMember) {
                            contactSlots.value[priorityIndex] = fullMember
                        }
                    }
                })
            } else {

            }

            
            const medRes = await api.get(`/families/${familyId}/medications`)
            if (medRes.data && medRes.data.length > 0) {

                medications.value = medRes.data.map(m => ({
                    ...m,
                    
                    
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
        
        const totalDosesDay = med.notificationTimes ? med.notificationTimes.length : 0
        medications.value.push({
            ...med,
            totalDosesDay
        })
    }
  };

    const removeMedication = (index) => {
        const target = medications.value[index]
        if (target.id) {
            
            deletedMedicationIds.value.push(target.id)
        }
        medications.value.splice(index, 1)
    }

    const saveData = async (familyId) => {
        try {
            
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



            
            await api.put(`/families/${familyId}`, payload)


            

            
            if (deletedMedicationIds.value.length > 0) {

                await Promise.all(deletedMedicationIds.value.map(id =>
                    api.delete(`/families/${familyId}/medications/${id}`)
                ))
            }

            
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
