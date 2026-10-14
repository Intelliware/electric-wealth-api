package com.electricwealth.api.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.AdvisorDtos.AdvisorInfo;
import com.electricwealth.api.dto.AdvisorDtos.AdvisorMessage;
import com.electricwealth.api.dto.AdvisorDtos.Appointment;
import com.electricwealth.api.dto.AdvisorDtos.AppointmentCreateRequest;
import com.electricwealth.api.dto.AdvisorDtos.AvailabilityDay;
import com.electricwealth.api.dto.AdvisorDtos.AvailabilityResponse;
import com.electricwealth.api.dto.AdvisorDtos.MessageSender;
import com.electricwealth.api.dto.ApiEnums.MeetingType;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdvisorRepository {

    /** Meeting start times offered every business day (Toronto time). */
    public static final List<String> SLOT_TIMES = List.of("09:00", "10:30", "12:00", "14:30", "16:00");
    private static final int BOOKABLE_DAYS = 5;
    private static final ZoneId TIME_ZONE = ZoneId.of("America/Toronto");

    private final JdbcClient jdbc;

    public AdvisorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The next n weekdays after {@code from}. */
    public static List<LocalDate> nextBusinessDays(LocalDate from, int n) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate d = from;
        while (days.size() < n) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(d);
            }
        }
        return days;
    }

    public Optional<AdvisorInfo> findUserAdvisor(String userId) {
        return jdbc.sql("SELECT a.* FROM advisors a JOIN users u ON u.advisor_id = a.id WHERE u.id = ?")
                .param(userId).query(Rows.of(AdvisorInfo.class)).optional();
    }

    private record SlotRow(String date, String time) {
    }

    /** Open slots for the advisor over the booking window. A slot is open if no scheduled meeting holds it. */
    public AvailabilityResponse getAvailability(AdvisorInfo advisor) {
        List<LocalDate> dates = nextBusinessDays(LocalDate.now(TIME_ZONE), BOOKABLE_DAYS);
        Set<String> taken = new HashSet<>();
        jdbc.sql("""
                SELECT date, time FROM appointments
                WHERE advisor_id = ? AND status = 'scheduled' AND date BETWEEN ? AND ?""")
                .params(advisor.id(), dates.getFirst().toString(), dates.getLast().toString())
                .query(Rows.of(SlotRow.class)).list()
                .forEach(s -> taken.add(s.date() + " " + s.time()));
        List<AvailabilityDay> days = dates.stream()
                .map(date -> new AvailabilityDay(date.toString(), advisor.available()
                        ? SLOT_TIMES.stream().filter(time -> !taken.contains(date + " " + time)).toList()
                        : List.of()))
                .toList();
        return new AvailabilityResponse(advisor, List.of(MeetingType.values()), days);
    }

    public boolean isSlotOpen(AdvisorInfo advisor, LocalDate date, String time) {
        return getAvailability(advisor).days().stream()
                .anyMatch(d -> d.date().equals(date.toString()) && d.slots().contains(time));
    }

    public List<Appointment> listAppointments(String userId) {
        return jdbc.sql("SELECT * FROM appointments WHERE user_id = ? ORDER BY date, time")
                .param(userId).query(Rows.of(Appointment.class)).list();
    }

    public Optional<Appointment> findAppointment(String userId, String id) {
        return jdbc.sql("SELECT * FROM appointments WHERE user_id = ? AND id = ?")
                .params(userId, id).query(Rows.of(Appointment.class)).optional();
    }

    public String createAppointment(String userId, String advisorId, AppointmentCreateRequest input) {
        String id = "appt-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("""
                INSERT INTO appointments (id, user_id, advisor_id, meeting_type, date, time, status, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'scheduled', ?, ?)""")
                .params(id, userId, advisorId, JsonEnums.value(input.meetingType()), input.date().toString(),
                        input.time(), SqlValues.blankToNull(input.notes()), SqlValues.isoNowSeconds())
                .update();
        return id;
    }

    public void rescheduleAppointment(String userId, String id, LocalDate date, String time) {
        jdbc.sql("UPDATE appointments SET date = ?, time = ? WHERE user_id = ? AND id = ?")
                .params(date.toString(), time, userId, id).update();
    }

    public void cancelAppointment(String userId, String id) {
        jdbc.sql("UPDATE appointments SET status = 'cancelled' WHERE user_id = ? AND id = ?").params(userId, id).update();
    }

    private record MessageRow(String id, MessageSender sender, String text, String sentAt) {

        AdvisorMessage toMessage() {
            return new AdvisorMessage(id, sender, text, sentAt);
        }
    }

    public List<AdvisorMessage> listMessages(String userId) {
        return jdbc.sql("SELECT * FROM advisor_messages WHERE user_id = ? ORDER BY sent_at, id")
                .param(userId).query(Rows.of(MessageRow.class)).list().stream()
                .map(MessageRow::toMessage)
                .toList();
    }

    public AdvisorMessage addClientMessage(String userId, String advisorId, String text) {
        var row = new MessageRow("msg-" + UUID.randomUUID().toString().substring(0, 8), MessageSender.CLIENT, text,
                SqlValues.isoNowSeconds());
        jdbc.sql("INSERT INTO advisor_messages (id, user_id, advisor_id, sender, text, sent_at) VALUES (?, ?, ?, ?, ?, ?)")
                .params(row.id(), userId, advisorId, JsonEnums.value(row.sender()), row.text(), row.sentAt())
                .update();
        return row.toMessage();
    }
}
