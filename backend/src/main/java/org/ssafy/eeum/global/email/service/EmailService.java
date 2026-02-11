package org.ssafy.eeum.global.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * JavaMailSender를 사용하여 사용자에게 인증 코드 등의 이메일을 발송하는 서비스 클래스입니다.
 * 
 * @summary 이메일 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    /**
     * 회원 가입 등을 위한 인증 코드를 지정된 이메일 주소로 비동기 전송합니다.
     * 
     * @summary 인증 코드 이메일 전송
     * @param to   수신자 이메일 주소
     * @param code 인증 코드 문자열
     */
    @Async
    public void sendVerificationCode(String to, String code) {
        String subject = "[이음] 이메일 인증 코드를 확인해주세요";

        String content = "<div style='margin:100px;'>" +
                "<h1>안녕하세요 이음입니다.</h1>" +
                "<br>" +
                "<p>아래 인증 코드를 입력해 이메일 인증을 완료해주세요.</p>" +
                "<br>" +
                "<div align='center' style='border:1px solid black; font-family:verdana';>" +
                "<h3 style='color:blue;'>회원가입 인증 코드입니다.</h3>" +
                "<div style='font-size:130%'>" +
                "<strong>" + code + "</strong>" +
                "</div><br/>" +
                "</div>";

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email", e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
