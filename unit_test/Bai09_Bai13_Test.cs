using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai09_Bai13_Test
    {
        // =========================
        // BÀI 09 - ThayThe
        // =========================

        [TestMethod]
        public void Bai09_TC01()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "Truong dai hoc cong nghiep";
            string actual = o.ThayThe("Truong dh cong nghiep", "dh", "dai hoc");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC02()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "XX";
            string actual = o.ThayThe("abab", "ab", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC03()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "abc";
            string actual = o.ThayThe("abc", "z", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC04()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "";
            string actual = o.ThayThe("", "", "");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC05()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "abc";
            string actual = o.ThayThe("abc", "", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC06()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "acac";
            string actual = o.ThayThe("abcabc", "b", "");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC07()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "XCD";
            string actual = o.ThayThe("abCD", "ab", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC08()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "CDX";
            string actual = o.ThayThe("CDab", "ab", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC09()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "";
            string actual = o.ThayThe("", "a", "X");
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai09_TC10()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string expected = "X";
            string actual = o.ThayThe("abc", "abc", "X");
            Assert.AreEqual(expected, actual);
        }

        // =========================
        // BÀI 13 - Max
        // =========================

        [TestMethod]
        public void Bai13_TC01()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 10;
            int actual = o.Max(10, 5, 2);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC02()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 10;
            int actual = o.Max(2, 10, 5);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC03()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 10;
            int actual = o.Max(2, 5, 10);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC04()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 7;
            int actual = o.Max(7, 7, 7);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC05()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 3;
            int actual = o.Max(1, 2, 3);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC06()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int expected = 50;
            int actual = o.Max(50, 49, 48);
            Assert.AreEqual(expected, actual);
        }

        [TestMethod]
        public void Bai13_TC07()
        {
            Exception expectedException = null;
            try
            {
                MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
                o.Max(0, 10, 10);
            }
            catch (Exception ex)
            {
                expectedException = ex;
            }

            Assert.IsNotNull(expectedException);
            Assert.IsInstanceOfType(expectedException, typeof(IndexOutOfRangeException));
        }

        [TestMethod]
        public void Bai13_TC08()
        {
            Exception expectedException = null;
            try
            {
                MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
                o.Max(10, 51, 10);
            }
            catch (Exception ex)
            {
                expectedException = ex;
            }

            Assert.IsNotNull(expectedException);
            Assert.IsInstanceOfType(expectedException, typeof(IndexOutOfRangeException));
        }

        [TestMethod]
        public void Bai13_TC09()
        {
            Exception expectedException = null;
            try
            {
                MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
                o.Max(10, 10, -1);
            }
            catch (Exception ex)
            {
                expectedException = ex;
            }

            Assert.IsNotNull(expectedException);
            Assert.IsInstanceOfType(expectedException, typeof(IndexOutOfRangeException));
        }

        [TestMethod]
        public void Bai13_TC10()
        {
            Exception expectedException = null;
            try
            {
                MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
                o.Max(99, 10, 10);
            }
            catch (Exception ex)
            {
                expectedException = ex;
            }

            Assert.IsNotNull(expectedException);
            Assert.IsInstanceOfType(expectedException, typeof(IndexOutOfRangeException));
        }
    }
}
