import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '@/services/api';
import { useFamilyStore } from '@/stores/family';
import { Logger } from '@/services/logger';

/**
 * 그룹 설정(기본 정보, 질환, 비상 연락처, 복약 정보)을 관리하는 Pinia 스토어입니다.
 * @summary 그룹 설정 관리 스토어
 */
export const useGroupSetupStore = defineStore('groupSetup', () => {
  /** @type {import('vue').Ref<boolean>} 초기화 여부 */
  const isInitialized = ref(false);

  /** @type {import('vue').Ref<number|null>} 현재 대상 가족 ID */
  const currentFamilyId = ref(null);

  /** 단계 1: 그룹 이름 */
  const groupName = ref('');

  /** 단계 2: 건강 정보 */
  const seniorId = ref(null);
  const bloodType = ref('');
  const diseases = ref([]);

  /** 단계 3: 비상 연락망 */
  const contactSlots = ref([null, null, null]);

  /** 단계 4: 복약 정보 */
  const medications = ref([]);
  const deletedMedicationIds = ref([]);

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

  /**
   * 이미 해당 가족으로 초기화되었는지 확인합니다. (Internal)
   * @param {number|string} familyId
   */
  const isAlreadyInitialized = (familyId) =>
    isInitialized.value && currentFamilyId.value === familyId;

  /**
   * 그룹 기본 정보(이름 등)를 가져옵니다. (Internal)
   * @param {number|string} familyId
   */
  const fetchGroupBasicInfo = async (familyId) => {
    const familyStore = useFamilyStore();
    if (familyStore.families.length === 0) await familyStore.fetchFamilies();

    const family = familyStore.families.find((f) => String(f.id) === String(familyId));
    if (family) groupName.value = family.name;
  };

  /**
   * 피부양자 프로필 및 건강 정보를 가져옵니다. (Internal)
   * @param {number|string} familyId
   */
  const fetchDependentProfileAndHealth = async (familyId) => {
    const membersRes = await api.get(`/families/${familyId}/members`);
    const members = membersRes.data;
    const targetMember = members.find((m) => m.dependent === true);

    if (targetMember) {
      seniorId.value = targetMember.userId || targetMember.id;
      const detailRes = await api.get(`/families/${familyId}/members/${seniorId.value}`);
      applyHealthDetail(detailRes.data);
    }
  };

  /**
   * 상세 건강 정보를 상태에 반영합니다. (Internal)
   * @param {Object} detail
   */
  const applyHealthDetail = (detail) => {
    if (!detail) return;
    if (detail.bloodType) bloodType.value = detail.bloodType;

    const sourceDiseases = detail.chronicDiseases || detail.diseases;
    diseases.value = parseDiseases(sourceDiseases);
  };

  /**
   * 질환 정보(String or Array)를 배열로 파싱합니다. (Internal)
   * @param {any} source
   */
  const parseDiseases = (source) => {
    if (!source) return [];
    if (Array.isArray(source)) return source;
    if (typeof source === 'string')
      return source
        .split(',')
        .map((d) => d.trim())
        .filter((d) => d);
    return [];
  };

  /**
   * 비상 연락처 우선순위를 가져옵니다. (Internal)
   * @param {number|string} familyId
   */
  const fetchEmergencyContacts = async (familyId) => {
    const [membersRes, detailRes] = await Promise.all([
      api.get(`/families/${familyId}/members`),
      api.get(`/families/${familyId}/details`),
    ]);
    const members = membersRes.data;
    const detail = detailRes.data;

    if (detail?.memberPriorities) {
      detail.memberPriorities.forEach((p) => {
        const idx = p.emergencyPriority - 1;
        if (idx >= 0 && idx < 3) {
          const member = members.find((m) => (m.userId || m.id) === p.userId);
          if (member) contactSlots.value[idx] = member;
        }
      });
    }
  };

  /**
   * 복약 정보를 가져옵니다. (Internal)
   * @param {number|string} familyId
   */
  const fetchMedications = async (familyId) => {
    const res = await api.get(`/families/${familyId}/medications`);
    medications.value = res.data || [];
  };

  const handleInitError = (error) => {
    Logger.error('그룹 설정 데이터 초기화 실패:', error);
    reset();
  };

  /**
   * 새로운 약 정보를 추가합니다.
   * @param {Object} med 약 정보
   */
  const addMedication = (med) => {
    const totalDosesDay = med.notificationTimes?.length || 0;
    medications.value.push({ ...med, totalDosesDay });
  };

  /**
   * 약 정보를 제거합니다.
   * @param {number} index 인덱스
   */
  const removeMedication = (index) => {
    const target = medications.value[index];
    if (target?.id) deletedMedicationIds.value.push(target.id);
    medications.value.splice(index, 1);
  };

  /**
   * 변경된 설정 데이터를 서버에 저장합니다.
   * @param {number|string} familyId 가족 ID
   */
  const saveData = async (familyId) => {
    try {
      await updateGroupInfo(familyId);
      await syncMedications(familyId);
    } catch (error) {
      Logger.error('그룹 설정 저장 실패:', error);
      throw error;
    }
  };

  /**
   * 그룹 기본 정보 및 우선순위를 업데이트합니다. (Internal)
   * @param {number|string} familyId
   */
  const updateGroupInfo = async (familyId) => {
    const payload = {
      newGroupName: groupName.value,
      dependentUserId: seniorId.value,
      dependentBloodType: bloodType.value,
      dependentChronicDiseases: diseases.value,
      memberPriorities: contactSlots.value
        .map((m, i) => (m ? { userId: m.userId || m.id, emergencyPriority: i + 1 } : null))
        .filter((p) => p),
    };
    await api.put(`/families/${familyId}`, payload);
  };

  /**
   * 약 정보의 변경사항(삭제/추가)을 동기화합니다. (Internal)
   * @param {number|string} familyId
   */
  const syncMedications = async (familyId) => {
    if (deletedMedicationIds.value.length > 0) {
      await Promise.all(
        deletedMedicationIds.value.map((id) =>
          api.delete(`/families/${familyId}/medications/${id}`),
        ),
      );
    }

    const newMeds = medications.value.filter((m) => !m.id);
    if (newMeds.length > 0) {
      const medPayload = newMeds.map((m) => ({
        medicineName: m.medicineName,
        cycleType: m.cycleType,
        totalDosesDay: m.totalDosesDay,
        cycleValue: m.cycleValue,
        daysOfWeek: m.daysOfWeek,
        startDate: m.startDate,
        endDate: m.endDate,
        notificationTimes: m.notificationTimes,
      }));
      await api.post(`/families/${familyId}/medications`, medPayload);
    }
  };

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
    reset,
  };
});
