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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

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
