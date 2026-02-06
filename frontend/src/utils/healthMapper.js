export const mapSamsungHealthToBackend = (dataString) => {
    try {
        if (!dataString) return null;
        const data = JSON.parse(dataString);

        const now = new Date();
        const localIso = new Date(now.getTime() - (now.getTimezoneOffset() * 60000)).toISOString().split('.')[0];

        return {
            recordDate: data.record_date ? data.record_date.replace(' ', 'T') : localIso,
            steps: data.steps || 0,
            restingHeartRate: data.resting_heart_rate || 0,
            averageHeartRate: data.average_heart_rate || 0,
            maxHeartRate: data.max_heart_rate || 0,
            sleepTotalMinutes: data.sleep_total_minutes || 0,
            sleepDeepMinutes: data.sleep_deep_minutes || 0,
            sleepLightMinutes: data.sleep_light_minutes || 0,
            sleepRemMinutes: data.sleep_rem_minutes || 0,
            bloodOxygen: data.blood_oxygen || 0,
            bloodGlucose: data.blood_glucose || 0,
            systolicPressure: data.systolic_pressure || 0,
            diastolicPressure: data.diastolic_pressure || 0,
            activeCalories: data.active_calories || 0,
            activeMinutes: data.active_minutes || 0
        };
    } catch (e) {
        console.error("HealthMapper Error:", e);
        return null;
    }
};
